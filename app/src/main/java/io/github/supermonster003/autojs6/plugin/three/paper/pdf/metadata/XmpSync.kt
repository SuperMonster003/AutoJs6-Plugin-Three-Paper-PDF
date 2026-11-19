package io.github.supermonster003.autojs6.plugin.three.paper.pdf.metadata

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Brings an XMP packet in line with `setInfo` (roadmap D66): `title` is `dc:title` and `subject` `dc:description` (a
 * language alternative with one `x-default` entry), `author` is `dc:creator` (an ordered list with one entry),
 * `keywords` is `pdf:Keywords` and `creator` `xmp:CreatorTool` (simple values). A key given with a value replaces the
 * property wherever it is (an element or an attribute of any `rdf:Description`) by one element in the first
 * `rdf:Description`; a key given as null removes the property; keys not given are left alone. The packet is read with
 * the platform XML parser, which never sees a document type declaration (such a packet is refused as unparsable, so no
 * entity is ever expanded), and written back without an XML declaration, keeping the `xpacket` processing
 * instructions. [apply] answers null when the packet cannot be parsed or has no `rdf:RDF`.
 * zh-CN: 使 XMP 数据包与 `setInfo` 一致 (路线图 D66): `title` 对应 `dc:title`, `subject` 对应 `dc:description` (只有一个
 * `x-default` 项的语言候选), `author` 对应 `dc:creator` (只有一项的有序列表), `keywords` 对应 `pdf:Keywords`, `creator` 对应
 * `xmp:CreatorTool` (简单值). 给出值的键把该属性 (无论是哪个 `rdf:Description` 的元素还是属性) 换成第一个 `rdf:Description` 里的一个
 * 元素; 给出 null 的键删除该属性; 未给出的键不变. 数据包用平台 XML 解析器读取, 解析器不会见到文档类型声明 (带声明的数据包按无法
 * 解析拒绝, 因此从不展开实体), 写回时不带 XML 声明, 保留 `xpacket` 处理指令. 数据包无法解析或没有 `rdf:RDF` 时 [apply] 返回 null.
 */
internal object XmpSync {

    const val RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    const val DC = "http://purl.org/dc/elements/1.1/"
    const val PDF = "http://ns.adobe.com/pdf/1.3/"
    const val XMP = "http://ns.adobe.com/xap/1.0/"
    private const val XML_NS = "http://www.w3.org/XML/1998/namespace"
    private const val XMLNS = "http://www.w3.org/2000/xmlns/"

    /** How one `setInfo` key is written in XMP. zh-CN: 一个 `setInfo` 键在 XMP 中的写法. */
    enum class Shape { ALT, SEQ, SIMPLE }

    /** One mapped property. zh-CN: 一个映射的属性. */
    class Property(val namespace: String, val prefix: String, val name: String, val shape: Shape)

    /** The D66 mapping by `setInfo` key. zh-CN: D66 的映射, 按 `setInfo` 的键. */
    val PROPERTIES: Map<String, Property> = linkedMapOf(
        InfoChanges.TITLE to Property(DC, "dc", "title", Shape.ALT),
        InfoChanges.AUTHOR to Property(DC, "dc", "creator", Shape.SEQ),
        InfoChanges.SUBJECT to Property(DC, "dc", "description", Shape.ALT),
        InfoChanges.KEYWORDS to Property(PDF, "pdf", "Keywords", Shape.SIMPLE),
        InfoChanges.CREATOR to Property(XMP, "xmp", "CreatorTool", Shape.SIMPLE),
    )

    /**
     * [packet] with the [changes] applied (`setInfo` keys to a value or null), or null when it cannot be parsed.
     * zh-CN: 应用 [changes] (`setInfo` 键到值或 null) 后的 [packet]; 无法解析时为 null.
     */
    fun apply(packet: ByteArray, changes: Map<String, String?>): ByteArray? {
        val document = parse(packet) ?: return null
        val rdf = document.getElementsByTagNameNS(RDF, "RDF").item(0) as? Element ?: return null
        val descriptions = children(rdf).filter { it.namespaceURI == RDF && it.localName == "Description" }
        val target = descriptions.firstOrNull() ?: document.createElementNS(RDF, prefixOf(rdf, "rdf") + ":Description").also {
            it.setAttributeNS(RDF, prefixOf(rdf, "rdf") + ":about", "")
            rdf.appendChild(it)
        }
        val rdfPrefix = prefixOf(rdf, "rdf")
        for ((key, value) in changes) {
            val property = PROPERTIES[key] ?: continue
            for (description in descriptions) {
                description.removeAttributeNS(property.namespace, property.name)
                children(description).filter { it.namespaceURI == property.namespace && it.localName == property.name }.forEach { description.removeChild(it) }
            }
            if (value == null) continue
            val prefix = target.lookupPrefix(property.namespace) ?: property.prefix.also {
                target.setAttributeNS(XMLNS, "xmlns:$it", property.namespace)
            }
            val element = document.createElementNS(property.namespace, "$prefix:${property.name}")
            when (property.shape) {
                Shape.SIMPLE -> element.textContent = value
                Shape.ALT, Shape.SEQ -> {
                    val container = document.createElementNS(RDF, "$rdfPrefix:" + if (property.shape == Shape.ALT) "Alt" else "Seq")
                    val item = document.createElementNS(RDF, "$rdfPrefix:li")
                    if (property.shape == Shape.ALT) item.setAttributeNS(XML_NS, "xml:lang", "x-default")
                    item.textContent = value
                    container.appendChild(item)
                    element.appendChild(container)
                }
            }
            target.appendChild(element)
        }
        return serialize(document)
    }

    private fun parse(packet: ByteArray): Document? {
        // A document type declaration is refused before parsing: no entity is ever expanded or fetched.
        // zh-CN: 解析之前拒绝文档类型声明: 从不展开或获取任何实体.
        if (String(packet, Charsets.ISO_8859_1).contains("<!DOCTYPE", ignoreCase = true)) return null
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isExpandEntityReferences = false
        runCatching { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        return try {
            factory.newDocumentBuilder().apply { setErrorHandler(null) }.parse(ByteArrayInputStream(packet))
        } catch (_: Exception) {
            null
        }
    }

    private fun serialize(document: Document): ByteArray? = try {
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        val output = ByteArrayOutputStream()
        transformer.transform(DOMSource(document), StreamResult(output))
        output.toByteArray()
    } catch (_: Exception) {
        null
    }

    private fun children(element: Element): List<Element> {
        val nodes = element.childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private fun prefixOf(node: Node, fallback: String): String = node.prefix ?: node.lookupPrefix(RDF) ?: fallback

}
