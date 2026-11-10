package com.strmr.mobile.cast

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * A small element tree with namespace prefixes dropped: enough for UPnP device descriptions
 * and SOAP replies, and plain JVM code so the parsing is unit tested.
 */
class XmlNode(val name: String, val text: String, val children: List<XmlNode>) {
    fun child(name: String): XmlNode? = children.firstOrNull { it.name == name }

    /** Every element under this one called [name], depth first. */
    fun descendants(name: String): List<XmlNode> =
        children.flatMap { (if (it.name == name) listOf(it) else emptyList()) + it.descendants(name) }

    /** The text of the child called [name], trimmed; null when there is no such child. */
    fun childText(name: String): String? = child(name)?.text?.trim()

    companion object {
        /** The document's root element, or null for something that isn't XML. */
        fun parse(xml: ByteArray): XmlNode? = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                // A renderer's description is never trusted to pull in other files.
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                isExpandEntityReferences = false
            }
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
            convert(doc.documentElement)
        }.getOrNull()

        fun parse(xml: String): XmlNode? = parse(xml.toByteArray(Charsets.UTF_8))

        private fun convert(element: Element): XmlNode {
            val children = mutableListOf<XmlNode>()
            val text = StringBuilder()
            val nodes = element.childNodes
            for (i in 0 until nodes.length) {
                when (val node = nodes.item(i)) {
                    is Element -> children += convert(node)
                    else -> if (node.nodeType == org.w3c.dom.Node.TEXT_NODE || node.nodeType == org.w3c.dom.Node.CDATA_SECTION_NODE) {
                        text.append(node.nodeValue)
                    }
                }
            }
            return XmlNode(element.localName ?: element.nodeName.substringAfter(':'), text.toString(), children)
        }
    }
}
