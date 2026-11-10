package com.strmr.tv.player.dv7.mkv;

import androidx.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/** Collects simple Matroska chapter metadata from EBML element callbacks. */
final class MatroskaChapterParser {
  static final int ID_CHAPTERS = 0x1043A770;
  static final int ID_EDITION_ENTRY = 0x45B9;
  static final int ID_EDITION_FLAG_HIDDEN = 0x45BD;
  static final int ID_EDITION_FLAG_DEFAULT = 0x45DB;
  static final int ID_CHAPTER_ATOM = 0xB6;
  static final int ID_CHAPTER_TIME_START = 0x91;
  static final int ID_CHAPTER_TIME_END = 0x92;
  static final int ID_CHAPTER_FLAG_HIDDEN = 0x98;
  static final int ID_CHAPTER_FLAG_ENABLED = 0x4598;
  static final int ID_CHAPTER_DISPLAY = 0x80;
  static final int ID_CHAP_STRING = 0x85;

  private final MatroskaExtractor.ChapterListener listener;
  private final List<Edition> editions = new ArrayList<>();
  private final Deque<ChapterBuilder> chapterStack = new ArrayDeque<>();
  @Nullable private Edition currentEdition;

  MatroskaChapterParser(@Nullable MatroskaExtractor.ChapterListener listener) {
    this.listener = listener;
  }

  int getElementType(int id) {
    switch (id) {
      case ID_CHAPTERS:
      case ID_EDITION_ENTRY:
      case ID_CHAPTER_ATOM:
      case ID_CHAPTER_DISPLAY:
        return EbmlProcessor.ELEMENT_TYPE_MASTER;
      case ID_EDITION_FLAG_HIDDEN:
      case ID_EDITION_FLAG_DEFAULT:
      case ID_CHAPTER_TIME_START:
      case ID_CHAPTER_TIME_END:
      case ID_CHAPTER_FLAG_HIDDEN:
      case ID_CHAPTER_FLAG_ENABLED:
        return EbmlProcessor.ELEMENT_TYPE_UNSIGNED_INT;
      case ID_CHAP_STRING:
        return EbmlProcessor.ELEMENT_TYPE_STRING;
      default:
        return EbmlProcessor.ELEMENT_TYPE_UNKNOWN;
    }
  }

  boolean isLevel1Element(int id) {
    return id == ID_CHAPTERS;
  }

  void reset() {
    editions.clear();
    chapterStack.clear();
    currentEdition = null;
  }

  void startMasterElement(int id) {
    switch (id) {
      case ID_CHAPTERS:
        reset();
        break;
      case ID_EDITION_ENTRY:
        currentEdition = new Edition();
        break;
      case ID_CHAPTER_ATOM:
        chapterStack.push(new ChapterBuilder());
        break;
      default:
        break;
    }
  }

  void endMasterElement(int id) {
    switch (id) {
      case ID_CHAPTER_ATOM:
        if (chapterStack.isEmpty()) {
          return;
        }
        ChapterBuilder completed = chapterStack.pop();
        if (!chapterStack.isEmpty()) {
          chapterStack.peek().children.add(completed);
        } else if (currentEdition != null) {
          currentEdition.chapters.add(completed);
        }
        break;
      case ID_EDITION_ENTRY:
        if (currentEdition != null) {
          editions.add(currentEdition);
          currentEdition = null;
        }
        break;
      case ID_CHAPTERS:
        publishDefaultEdition();
        break;
      default:
        break;
    }
  }

  void integerElement(int id, long value) {
    switch (id) {
      case ID_EDITION_FLAG_HIDDEN:
        if (currentEdition != null) currentEdition.hidden = value == 1;
        break;
      case ID_EDITION_FLAG_DEFAULT:
        if (currentEdition != null) currentEdition.isDefault = value == 1;
        break;
      case ID_CHAPTER_TIME_START:
        if (!chapterStack.isEmpty()) chapterStack.peek().startTimeNs = value;
        break;
      case ID_CHAPTER_TIME_END:
        if (!chapterStack.isEmpty()) chapterStack.peek().endTimeNs = value;
        break;
      case ID_CHAPTER_FLAG_HIDDEN:
        if (!chapterStack.isEmpty()) chapterStack.peek().hidden = value == 1;
        break;
      case ID_CHAPTER_FLAG_ENABLED:
        if (!chapterStack.isEmpty()) chapterStack.peek().enabled = value != 0;
        break;
      default:
        break;
    }
  }

  void stringElement(int id, String value) {
    if (id == ID_CHAP_STRING && !chapterStack.isEmpty()) {
      ChapterBuilder chapter = chapterStack.peek();
      if (chapter.title.isEmpty() && value != null) {
        chapter.title = value.trim();
      }
    }
  }

  private void publishDefaultEdition() {
    if (listener == null) {
      return;
    }
    Edition selected = null;
    for (Edition edition : editions) {
      if (!edition.hidden && edition.isDefault) {
        selected = edition;
        break;
      }
    }
    if (selected == null) {
      for (Edition edition : editions) {
        if (!edition.hidden) {
          selected = edition;
          break;
        }
      }
    }
    if (selected == null) {
      listener.onChaptersParsed(Collections.emptyList());
      return;
    }

    List<MatroskaExtractor.Chapter> result = new ArrayList<>();
    for (ChapterBuilder chapter : selected.chapters) {
      appendVisibleChapters(chapter, result);
    }
    result.sort(Comparator.comparingLong(chapter -> chapter.startTimeUs));
    listener.onChaptersParsed(Collections.unmodifiableList(result));
  }

  private static void appendVisibleChapters(
      ChapterBuilder chapter, List<MatroskaExtractor.Chapter> output) {
    if (!chapter.enabled || chapter.hidden) {
      return;
    }
    if (chapter.startTimeNs >= 0) {
      long endTimeUs = chapter.endTimeNs >= chapter.startTimeNs ? chapter.endTimeNs / 1000L : -1L;
      output.add(
          new MatroskaExtractor.Chapter(
              chapter.startTimeNs / 1000L, endTimeUs, chapter.title));
    }
    for (ChapterBuilder child : chapter.children) {
      appendVisibleChapters(child, output);
    }
  }

  private static final class Edition {
    final List<ChapterBuilder> chapters = new ArrayList<>();
    boolean hidden;
    boolean isDefault;
  }

  private static final class ChapterBuilder {
    final List<ChapterBuilder> children = new ArrayList<>();
    long startTimeNs = -1L;
    long endTimeNs = -1L;
    String title = "";
    boolean hidden;
    boolean enabled = true;
  }
}
