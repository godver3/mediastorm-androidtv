#include <jni.h>
#include <android/log.h>
#include <cstdint>
#include <cstring>
#include <vector>

#define LOG_TAG "StrmrDoviBridge"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

#ifndef DOVI_REAL_LINKED
#define DOVI_REAL_LINKED 0
#endif

#if DOVI_REAL_LINKED
extern "C" {
typedef struct DoviRpuOpaque DoviRpuOpaque;
typedef struct DoviData {
    const uint8_t* data;
    size_t len;
} DoviData;

DoviRpuOpaque* dovi_parse_unspec62_nalu(const uint8_t* buf, size_t len);
DoviRpuOpaque* dovi_parse_rpu(const uint8_t* buf, size_t len);
const char* dovi_rpu_get_error(const DoviRpuOpaque* ptr);
void dovi_rpu_free(DoviRpuOpaque* ptr);
int32_t dovi_convert_rpu_with_mode(DoviRpuOpaque* ptr, uint8_t mode);
const DoviData* dovi_write_unspec62_nalu(DoviRpuOpaque* ptr);
void dovi_data_free(const DoviData* data);
}

static bool dovi_has_error(const DoviRpuOpaque* rpu) {
    if (rpu == nullptr) return true;
    const char* error = dovi_rpu_get_error(rpu);
    return error != nullptr && error[0] != '\0';
}

static uint8_t conversion_mode(jint mode) {
    switch (mode) {
        case 0:
        case 1:
        case 2:
        case 3:
        case 4:
            return static_cast<uint8_t>(mode);
        case 5:
            return 4U;
        default:
            return 1U;
    }
}

static bool convert_rpu(const uint8_t* data, int size, jint mode, std::vector<uint8_t>& out) {
    DoviRpuOpaque* rpu = dovi_parse_unspec62_nalu(data, static_cast<size_t>(size));
    if (rpu == nullptr || dovi_has_error(rpu)) {
        if (rpu != nullptr) dovi_rpu_free(rpu);
        rpu = dovi_parse_rpu(data, static_cast<size_t>(size));
        if (rpu != nullptr && dovi_has_error(rpu)) {
            dovi_rpu_free(rpu);
            rpu = nullptr;
        }
    }
    if (rpu == nullptr) return false;

    if (dovi_convert_rpu_with_mode(rpu, conversion_mode(mode)) < 0) {
        dovi_rpu_free(rpu);
        return false;
    }

    const DoviData* encoded = dovi_write_unspec62_nalu(rpu);
    if (encoded == nullptr || encoded->data == nullptr || encoded->len == 0U) {
        if (encoded != nullptr) dovi_data_free(encoded);
        dovi_rpu_free(rpu);
        return false;
    }

    out.assign(encoded->data, encoded->data + encoded->len);
    if (out.size() >= 2) {
        out[0] &= 0xFE;
        out[1] &= 0x07;
    }
    dovi_data_free(encoded);
    dovi_rpu_free(rpu);
    return true;
}
#endif

static int find_start_code(const uint8_t* data, int from, int limit) {
    for (int i = from; i + 2 < limit; ++i) {
        if (data[i] == 0 && data[i + 1] == 0) {
            if (data[i + 2] == 1) return i;
            if (i + 3 < limit && data[i + 2] == 0 && data[i + 3] == 1) return i;
        }
    }
    return -1;
}

static int start_code_length(const uint8_t* data, int offset, int limit) {
    return offset + 3 < limit
        && data[offset] == 0
        && data[offset + 1] == 0
        && data[offset + 2] == 0
        && data[offset + 3] == 1 ? 4 : 3;
}

static int nal_type(const uint8_t* data) {
    return (data[0] >> 1) & 0x3F;
}

static int layer_id(const uint8_t* data, int size) {
    if (size < 2) return 0;
    return ((data[0] & 0x01) << 5) | ((data[1] >> 3) & 0x1F);
}

static void write_length(std::vector<uint8_t>& out, int value, int bytes) {
    for (int i = bytes - 1; i >= 0; --i) {
        out.push_back(static_cast<uint8_t>((value >> (i * 8)) & 0xFF));
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_strmr_tv_player_dv7_DoviBridge_nativeIsConversionPathReady(
    JNIEnv*,
    jclass
) {
#if DOVI_REAL_LINKED
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_com_strmr_tv_player_dv7_DoviBridge_nativeConvertRpuNal(
    JNIEnv* env,
    jclass,
    jbyteArray sample,
    jint offset,
    jint len,
    jbyteArray out_buffer,
    jint mode
) {
#if DOVI_REAL_LINKED
    if (sample == nullptr || out_buffer == nullptr || offset < 0 || len <= 0) return 0;
    const jsize sample_len = env->GetArrayLength(sample);
    if (offset + len > sample_len) return 0;
    thread_local std::vector<uint8_t> input;
    thread_local std::vector<uint8_t> output;
    input.resize(static_cast<size_t>(len));
    env->GetByteArrayRegion(sample, offset, len, reinterpret_cast<jbyte*>(input.data()));
    if (!convert_rpu(input.data(), len, mode, output)) return 0;
    const jsize out_len = static_cast<jsize>(output.size());
    const jsize capacity = env->GetArrayLength(out_buffer);
    if (out_len > capacity) return -out_len;
    env->SetByteArrayRegion(out_buffer, 0, out_len, reinterpret_cast<const jbyte*>(output.data()));
    return out_len;
#else
    (void)env;
    (void)sample;
    (void)offset;
    (void)len;
    (void)out_buffer;
    (void)mode;
    return 0;
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_com_strmr_tv_player_dv7_DoviBridge_nativeProcessVideoSample(
    JNIEnv* env,
    jclass,
    jbyteArray sample,
    jint sample_len,
    jint nal_format,
    jint nal_length_field_length,
    jbyteArray out_buffer,
    jboolean convert_dovi,
    jint dovi_mode,
    jboolean strip_dovi
) {
    if (sample == nullptr || out_buffer == nullptr || sample_len <= 0) return 0;
    if (nal_format == 1 && (nal_length_field_length < 1 || nal_length_field_length > 4)) return 0;

    thread_local std::vector<uint8_t> input;
    thread_local std::vector<uint8_t> output;
    thread_local std::vector<uint8_t> processed;

    input.resize(static_cast<size_t>(sample_len));
    env->GetByteArrayRegion(sample, 0, sample_len, reinterpret_cast<jbyte*>(input.data()));
    output.clear();
    output.reserve(static_cast<size_t>(sample_len));
    bool changed = false;

    auto process_nal = [&](const uint8_t* nal, int nal_size) -> const std::vector<uint8_t>* {
        processed.clear();
        if (nal_size <= 0) return nullptr;
        const int type = nal_type(nal);
        const bool is_rpu = type == 62;
        if (convert_dovi && is_rpu) {
#if DOVI_REAL_LINKED
            if (convert_rpu(nal, nal_size, dovi_mode, processed)) return &processed;
#endif
            processed.assign(nal, nal + nal_size);
            if (processed.size() >= 2) {
                processed[0] &= 0xFE;
                processed[1] &= 0x07;
            }
            return &processed;
        }
        return nullptr;
    };

    if (nal_format == 1) {
        int pos = 0;
        while (pos + nal_length_field_length <= sample_len) {
            int nal_size = 0;
            for (int i = 0; i < nal_length_field_length; ++i) {
                nal_size = (nal_size << 8) | (input[pos + i] & 0xFF);
            }
            const int nal_start = pos + nal_length_field_length;
            if (nal_size <= 0 || nal_start + nal_size > sample_len) return 0;

            const uint8_t* nal = input.data() + nal_start;
            const bool drop = (strip_dovi && nal_type(nal) == 62) || layer_id(nal, nal_size) > 0;
            const std::vector<uint8_t>* rewritten = drop ? nullptr : process_nal(nal, nal_size);

            if (drop) {
                changed = true;
            } else if (rewritten != nullptr) {
                changed = true;
                write_length(output, static_cast<int>(rewritten->size()), nal_length_field_length);
                output.insert(output.end(), rewritten->begin(), rewritten->end());
            } else {
                write_length(output, nal_size, nal_length_field_length);
                output.insert(output.end(), nal, nal + nal_size);
            }
            pos = nal_start + nal_size;
        }
        if (pos != sample_len) return 0;
    } else {
        int scan = 0;
        while (scan < sample_len) {
            const int start = find_start_code(input.data(), scan, sample_len);
            if (start < 0) {
                output.insert(output.end(), input.begin() + scan, input.end());
                break;
            }
            if (start > scan) {
                output.insert(output.end(), input.begin() + scan, input.begin() + start);
            }
            const int sc_len = start_code_length(input.data(), start, sample_len);
            const int nal_start = start + sc_len;
            const int next = find_start_code(input.data(), nal_start + 2, sample_len);
            const int nal_end = next < 0 ? sample_len : next;
            const int nal_size = nal_end - nal_start;
            const uint8_t* nal = input.data() + nal_start;
            const bool drop = nal_size > 0 && ((strip_dovi && nal_type(nal) == 62) || layer_id(nal, nal_size) > 0);
            const std::vector<uint8_t>* rewritten = drop || nal_size <= 0 ? nullptr : process_nal(nal, nal_size);

            if (drop) {
                changed = true;
            } else if (rewritten != nullptr) {
                changed = true;
                output.insert(output.end(), input.begin() + start, input.begin() + nal_start);
                output.insert(output.end(), rewritten->begin(), rewritten->end());
            } else {
                output.insert(output.end(), input.begin() + start, input.begin() + nal_end);
            }
            scan = nal_end;
        }
    }

    if (!changed) return 0;
    const jsize out_len = static_cast<jsize>(output.size());
    const jsize capacity = env->GetArrayLength(out_buffer);
    if (out_len > capacity) return -out_len;
    env->SetByteArrayRegion(out_buffer, 0, out_len, reinterpret_cast<const jbyte*>(output.data()));
    return out_len;
}
