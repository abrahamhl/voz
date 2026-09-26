package dev.auxdesign.voz.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import dev.auxdesign.voz.core.model.Lang
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.Executor
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class InMemoryKeyValueStore : KeyValueStore {
    val map = HashMap<String, String>()
    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

class SecretStoreContractTest {

    private fun aesKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = aesKey()
    private val kv = InMemoryKeyValueStore()
    private val store = EncryptedSecretStore(kv, AesGcmCipher { key })

    @Test
    fun `round trips and never stores plaintext`() {
        val plain = "example value for tests"
        assertTrue(store.put(SecretStore.GEMINI_API_KEY, plain))
        assertEquals(plain, store.get(SecretStore.GEMINI_API_KEY))
        val raw = kv.map.getValue(SecretStore.GEMINI_API_KEY)
        assertFalse(raw.contains(plain))
        assertFalse(String(Base64.getDecoder().decode(raw), Charsets.ISO_8859_1).contains(plain))
        assertTrue(store.has(SecretStore.GEMINI_API_KEY))
    }

    @Test
    fun `same value encrypts differently every time (random IV)`() {
        store.put("a", "same")
        val first = kv.map.getValue("a")
        store.put("a", "same")
        assertNotEquals(first, kv.map.getValue("a"))
    }

    @Test
    fun `remove deletes the value`() {
        store.put("a", "x")
        store.remove("a")
        assertNull(store.get("a"))
        assertFalse(store.has("a"))
    }

    @Test
    fun `tampered or foreign ciphertext yields null instead of garbage`() {
        store.put("a", "secret")
        val bytes = Base64.getDecoder().decode(kv.map.getValue("a"))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()
        kv.map["a"] = Base64.getEncoder().encodeToString(bytes)
        assertNull(store.get("a"))

        store.put("b", "secret")
        val otherKeyStore = EncryptedSecretStore(kv, AesGcmCipher { aesKey() })
        assertNull(otherKeyStore.get("b"))
        kv.map["c"] = "not base64 !!"
        assertNull(store.get("c"))
    }

    @Test
    fun `put reports failure when the keystore is unavailable`() {
        val broken = EncryptedSecretStore(kv, AesGcmCipher { error("keystore down") })
        assertFalse(broken.put("a", "x"))
        assertNull(kv.map["a"])
    }
}

class SettingsCodecTest {

    @Test
    fun `defaults when nothing is stored`() {
        assertEquals(VozSettings(), SettingsCodec.read(mutablePreferencesOf()))
    }

    @Test
    fun `round trip through preferences`() {
        val settings = VozSettings(
            language = Lang.NL,
            cloudEnabled = true,
            commentDepth = 9,
            speechRate = 1.5f,
            bubbleSize = BubbleSize.LARGE,
            highContrast = true,
            onboardingDone = true,
            cloudConsent = VozSettings.CLOUD_CONSENT_VERSION,
        )
        val prefs = mutablePreferencesOf()
        SettingsCodec.write(prefs, settings)
        assertEquals(settings, SettingsCodec.read(prefs))
    }

    @Test
    fun `the cloud stays off until the privacy notice is accepted`() {
        assertFalse(VozSettings(cloudEnabled = true).cloudAllowed)
        assertFalse(VozSettings(cloudEnabled = false, cloudConsent = VozSettings.CLOUD_CONSENT_VERSION).cloudAllowed)
        assertTrue(VozSettings(cloudEnabled = true, cloudConsent = VozSettings.CLOUD_CONSENT_VERSION).cloudAllowed)
    }

    @Test
    fun `out of range values are clamped and system language removes the key`() {
        val prefs = mutablePreferencesOf()
        SettingsCodec.write(prefs, VozSettings(language = Lang.ES, commentDepth = 99, speechRate = 9f))
        val read = SettingsCodec.read(prefs)
        assertEquals(VozSettings.MAX_COMMENT_DEPTH, read.commentDepth)
        assertEquals(VozSettings.MAX_RATE, read.speechRate)
        SettingsCodec.write(prefs, read.copy(language = null))
        assertNull(prefs[SettingsCodec.LANGUAGE])
        assertFalse(SettingsCodec.read(prefs).cloudEnabled)
    }
}

class ActionLogTest {

    private val direct = Executor { it.run() }

    @Test
    fun `persists, reloads, caps size and clears`(@TempDir dir: File) {
        val file = File(dir, "log.jsonl")
        var now = 1_000L
        val log = ActionLog(file, max = 3, clock = { now++ }, io = direct)
        log.add(LogEntry.Kind.HEARD, "abre youtube")
        log.add(LogEntry.Kind.PLAN, "open_app(youtube)")
        log.add(LogEntry.Kind.DONE, "open_app(youtube)")
        log.add(LogEntry.Kind.HEARD, "multi\nline")
        assertEquals(3, log.entries.value.size)
        assertEquals("multi line", log.entries.value.last().text)

        val reloaded = ActionLog(file, max = 3, io = direct)
        assertEquals(log.entries.value, reloaded.entries.value)

        reloaded.clear()
        assertTrue(reloaded.entries.value.isEmpty())
        assertTrue(ActionLog(file, io = direct).entries.value.isEmpty())
    }

    @Test
    fun `corrupt lines are skipped`(@TempDir dir: File) {
        val file = File(dir, "log.jsonl")
        file.writeText("garbage\n{\"at\":1,\"kind\":\"DONE\",\"text\":\"ok\"}\n")
        assertEquals(listOf(LogEntry(1, LogEntry.Kind.DONE, "ok")), ActionLog(file).entries.value)
    }

    @Test
    fun `history writes leave no temporary artifact`(@TempDir dir: File) {
        val file = File(dir, "log.jsonl")
        ActionLog(file, io = direct).add(LogEntry.Kind.HEARD, "length 12")
        assertTrue(file.isFile)
        assertFalse(File(dir, ".log.jsonl.tmp").exists())
    }
}
