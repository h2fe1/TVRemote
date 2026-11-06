package dev.companionremote.protocol.plist

/**
 * Helpers for NSKeyedArchiver-encoded plists, ported from pyatv
 * `protocols/companion/keyed_archiver.py` and
 * `plist_payloads/rti_text_operations.py`.
 */
object KeyedArchiver {

    /**
     * Read properties from a keyed archive by following UID references,
     * starting at `$top`. Returns null for a path that doesn't resolve.
     */
    /**
     * Fully resolve an NSKeyedArchiver plist into plain maps/lists/scalars.
     * UIDs are followed through `$objects`, class metadata is dropped and
     * recursion is depth-limited so a malformed archive cannot loop.
     */
    fun decodeArchive(archive: ByteArray): Any? {
        val root = runCatching { BinaryPlist.decode(archive) }.getOrNull() as? Map<*, *>
            ?: return null
        val objects = root["\$objects"] as? List<*> ?: return null
        val top = root["\$top"] as? Map<*, *> ?: return null

        fun resolve(value: Any?, depth: Int): Any? {
            if (depth > 32) return null
            return when (value) {
                is PlistUid -> objects.getOrNull(value.value.toInt())
                    ?.let { resolve(it, depth + 1) }
                is Map<*, *> -> value.entries
                    .filter { it.key != "\$class" }
                    .associate { it.key.toString() to resolve(it.value, depth + 1) }
                is List<*> -> value.map { resolve(it, depth + 1) }
                else -> value
            }
        }

        return resolve(top["root"], 0)
    }

    fun readArchiveProperties(archive: ByteArray, vararg paths: List<String>): List<Any?> {
        val root = runCatching { BinaryPlist.decode(archive) }.getOrNull() as? Map<*, *>
            ?: return paths.map { null }
        val objects = root["\$objects"] as? List<Any?> ?: return paths.map { null }
        return paths.map { path ->
            var element: Any? = root["\$top"]
            try {
                for (key in path) {
                    element = (element as Map<*, *>)[key] ?: return@map null
                    if (element is PlistUid) element = objects[element.value.toInt()]
                }
                element
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * The two fixed RTI payloads, ported 1:1 (object table order and UID
 * numbering preserved) from pyatv `rti_text_operations.py`. Byte-identical
 * with pyatv's plistlib output (golden vectors in tests).
 */
object RtiPayloads {

    /** NSKeyedArchiver payload that clears the focused text field. */
    fun clearTextPayload(sessionUuid: ByteArray): ByteArray = BinaryPlist.encode(
        linkedMapOf(
            "\$version" to 100000L,
            "\$archiver" to "RTIKeyedArchiver",
            "\$top" to linkedMapOf("textOperations" to PlistUid(1)),
            "\$objects" to listOf(
                "\$null",
                linkedMapOf(
                    "\$class" to PlistUid(7),
                    "targetSessionUUID" to PlistUid(5),
                    "keyboardOutput" to PlistUid(2),
                    "textToAssert" to PlistUid(4),
                ),
                linkedMapOf("\$class" to PlistUid(3)),
                classDict("TIKeyboardOutput"),
                "",
                linkedMapOf("NS.uuidbytes" to sessionUuid, "\$class" to PlistUid(6)),
                classDict("NSUUID"),
                classDict("RTITextOperations"),
            ),
        ),
    )

    /** NSKeyedArchiver payload that inserts [text] at the cursor. */
    fun inputTextPayload(sessionUuid: ByteArray, text: String): ByteArray = BinaryPlist.encode(
        linkedMapOf(
            "\$version" to 100000L,
            "\$archiver" to "RTIKeyedArchiver",
            "\$top" to linkedMapOf("textOperations" to PlistUid(1)),
            "\$objects" to listOf(
                "\$null",
                linkedMapOf(
                    "keyboardOutput" to PlistUid(2),
                    "\$class" to PlistUid(7),
                    "targetSessionUUID" to PlistUid(5),
                ),
                linkedMapOf(
                    "insertionText" to PlistUid(3),
                    "\$class" to PlistUid(4),
                ),
                text,
                classDict("TIKeyboardOutput"),
                linkedMapOf("NS.uuidbytes" to sessionUuid, "\$class" to PlistUid(6)),
                classDict("NSUUID"),
                classDict("RTITextOperations"),
            ),
        ),
    )

    private fun classDict(name: String) = linkedMapOf(
        "\$classname" to name,
        "\$classes" to listOf(name, "NSObject"),
    )
}
