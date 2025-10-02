package io.github.guillermo_david.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Servicio de seguridad: - PIN (4 dígitos recomendado). - Lockout global (3
 * intentos -> 30s). - "Recordar PIN" X minutos (sólo útil mientras la app está
 * viva; no sobrevive a reinicios). - Recuperación por Pregunta de seguridad
 * (reenvuelve CMK). - Código de recuperación (one-time; reenvuelve CMK y se
 * borra). - Cifrado/descifrado con AES-256-GCM usando CMK (Content Master Key)
 * en memoria.
 *
 * Almacenamiento (Preferences): - pin_salt, pin_iter, cmk_wrap, cmk_wrap_iv ->
 * CMK envuelta con PIN-KEK - q_text, q_salt, q_wrap, q_wrap_iv -> CMK envuelta
 * con respuesta pregunta - rc_salt, rc_wrap, rc_wrap_iv -> CMK envuelta con
 * código recuperación (one-time) - failed_attempts, lock_until -> lockout
 * global - remember_until -> recordar PIN hasta (epoch millis)
 *
 * NOTAS: - Nunca se guarda el PIN; sólo se guarda la CMK envuelta con clave
 * derivada (PBKDF2). - "Remember" no almacena CMK en disco; sólo una marca de
 * tiempo. Si reinicias, habrá que introducir PIN al menos una vez para cargar
 * CMK en memoria.
 */
public final class SecurityService {
	

	// ==== Singleton ====
	private static final SecurityService INSTANCE = new SecurityService();

	public static SecurityService getInstance() {
		return INSTANCE;
	}

	private SecurityService() {
	}

	// ==== Preferences / keys ====
	private static final Preferences PREFS = Preferences.userNodeForPackage(SecurityService.class);

	private static final String K_PIN_SALT = "sec.pin_salt";
	private static final String K_PIN_ITER = "sec.pin_iter";
	private static final String K_CMK_WRAP = "sec.cmk_wrap";
	private static final String K_CMK_WRAP_IV = "sec.cmk_wrap_iv";

	private static final String K_Q_TEXT = "sec.q_text";
	private static final String K_Q_SALT = "sec.q_salt";
	private static final String K_Q_WRAP = "sec.q_wrap";
	private static final String K_Q_WRAP_IV = "sec.q_wrap_iv";

	private static final String K_RC_SALT = "sec.rc_salt";
	private static final String K_RC_WRAP = "sec.rc_wrap";
	private static final String K_RC_WRAP_IV = "sec.rc_wrap_iv";

	private static final String K_FAILED = "sec.failed_attempts";
	private static final String K_LOCK_UNTIL = "sec.lock_until";
	private static final String K_LOCK_CYCLES = "sec.lock_cycles";
	private static final String K_REMEMBER_UNTIL = "sec.remember_until";

	// ==== Parámetros criptográficos ====
	private static final int PBKDF2_ITERATIONS_DEFAULT = 200_000;
	private static final int PBKDF2_KEY_BITS = 256;
	private static final int SALT_LEN = 16;
	private static final int GCM_IV_LEN = 12;
	private static final int GCM_TAG_BITS = 128;
	
	private static final long DEFAULT_UNLOCK_TTL_MS = 5 * 60_000L; // 5 min
	private volatile long cmkValidUntil = Long.MAX_VALUE; // indefinido por defecto

	private static final SecureRandom RNG = new SecureRandom();

	// ==== Estado en memoria ====
	/** Content Master Key en memoria (32 bytes). */
	private volatile byte[] cmk; // null si no cargada

	// ==== Types ====
	public static final class Encrypted {
		public final byte[] cipher;
		public final byte[] iv;

		public Encrypted(byte[] c, byte[] i) {
			this.cipher = Objects.requireNonNull(c);
			this.iv = Objects.requireNonNull(i);
		}
	}

	private static final int MAX_FAILED = 3;
	private static final long LOCKOUT_MS = 30_000L; // 30s
	
	
	
	// === Getters ligeros para UI de recuperación ===
	public String getSecurityQuestion() {
	    return PREFS.get(K_Q_TEXT, null);
	}
	public boolean hasSecurityQuestion() {
	    return PREFS.get(K_Q_TEXT, null) != null
	        && PREFS.getByteArray(K_Q_WRAP, null) != null;
	}
	public boolean hasRecoveryCode() {
	    return PREFS.getByteArray(K_RC_WRAP, null) != null;
	}
	
	
	// ==== Lockout global ====
	public boolean isUnlocked() { return this.cmk != null; }

	public boolean isLockedOut() {
		return System.currentTimeMillis() < PREFS.getLong(K_LOCK_UNTIL, 0L);
	}

	public long lockoutRemainingMillis() {
		long rem = PREFS.getLong(K_LOCK_UNTIL, 0L) - System.currentTimeMillis();
		return Math.max(0L, rem);
	}

	public void clearLockout() {
		PREFS.putInt(K_FAILED, 0);
		PREFS.putLong(K_LOCK_UNTIL, 0L);
	}
	
	public int attemptsLeft() {
	    if (isLockedOut()) return 0;
	    int f = PREFS.getInt(K_FAILED, 0);
	    return Math.max(0, MAX_FAILED - f);
	}

	public synchronized void lockNow() {
	    this.cmk = null;
	    this.cmkValidUntil = 0L;                 // 👈 caduca ya
	    PREFS.putLong(K_REMEMBER_UNTIL, 0L);     // 👈 cancela “recordar”
	}


	// ==== Setup PIN ====
	public boolean hasPin() {
		return PREFS.getByteArray(K_CMK_WRAP, null) != null;
	}

	private static boolean isSixDigits(char[] pin) {
		if (pin == null || pin.length != 6)
			return false;
		for (char c : pin)
			if (c < '0' || c > '9')
				return false;
		return true;
	}

	private static void requireValidPin(char[] pin) {
		if (!isSixDigits(pin))
			throw new IllegalArgumentException("PIN debe tener 6 dígitos");
	}

	/**
	 * Crea el PIN por primera vez. Genera una CMK aleatoria y la envuelve con
	 * KEK(PIN).
	 */
	public synchronized void setupPin(char[] pin) {
		requireValidPin(pin);
		if (hasPin())
			throw new IllegalStateException("PIN ya configurado");

		byte[] newCmk = randomBytes(32);
		byte[] salt = randomBytes(SALT_LEN);
		int iter = PBKDF2_ITERATIONS_DEFAULT;

		try {
			byte[] kek = deriveKey(pin, salt, iter);
			byte[] iv = randomBytes(GCM_IV_LEN);
			byte[] wrap = aesGcmEncrypt(kek, newCmk, iv);

			PREFS.putByteArray(K_PIN_SALT, salt);
			PREFS.putInt(K_PIN_ITER, iter);
			PREFS.putByteArray(K_CMK_WRAP, wrap);
			PREFS.putByteArray(K_CMK_WRAP_IV, iv);

			// Reset lockout
			clearLockout();

			// Carga en memoria
			this.cmk = newCmk;
		} finally {
			zero(pin);
			// no limpiar newCmk: lo necesitamos en memoria
		}
	}

	/**
	 * Verifica PIN y carga la CMK en memoria.
	 */
	public synchronized boolean verifyPin(char[] pin) {
		requireValidPin(pin);

		if (isLockedOut())
			return false;

		byte[] salt = PREFS.getByteArray(K_PIN_SALT, null);
		byte[] wrap = PREFS.getByteArray(K_CMK_WRAP, null);
		byte[] iv = PREFS.getByteArray(K_CMK_WRAP_IV, null);
		int iter = PREFS.getInt(K_PIN_ITER, PBKDF2_ITERATIONS_DEFAULT);

		if (salt == null || wrap == null || iv == null) {
			zero(pin);
			return false;
		}

		try {
			byte[] kek = deriveKey(pin, salt, iter);
			byte[] plain = aesGcmDecrypt(kek, wrap, iv); // AEADBadTag si PIN incorrecto
			this.cmk = plain;
			
			this.cmkValidUntil = System.currentTimeMillis() + DEFAULT_UNLOCK_TTL_MS;

			// éxito -> limpia lockout
			clearLockout();
			PREFS.putInt(K_LOCK_CYCLES, 0); // éxito -> resetea ciclos
			return true;
		} catch (AEADBadTagException bad) {
		    int f = PREFS.getInt(K_FAILED, 0) + 1;
		    PREFS.putInt(K_FAILED, f);

		    if (f >= MAX_FAILED) {
		        // lee ciclos previos
		        int cycles = PREFS.getInt(K_LOCK_CYCLES, 0) + 1;
		        PREFS.putInt(K_LOCK_CYCLES, cycles);

		        long penalty = LOCKOUT_MS * (1L << (cycles - 1)); 
		        // 1º ciclo = 30s, 2º = 60s, 3º = 120s, etc.

		        PREFS.putLong(K_LOCK_UNTIL, System.currentTimeMillis() + penalty);
		        PREFS.putInt(K_FAILED, 0); // reinicia intentos
		    }
		    return false;
		} catch (GeneralSecurityException e) {
			return false;
		} finally {
			zero(pin);
		}
	}

	/**
	 * Cambia el PIN: reenvuelve la CMK con un nuevo KEK derivado del nuevo PIN.
	 * Requiere que la CMK esté cargada (o verificar explícitamente antes).
	 */
	public synchronized void changePin(char[] oldPin, char[] newPin) {
		requireValidPin(newPin);
		try {
			// Aceptamos oldPin null si ya hay cmk en memoria (p.ej. tras verifyPin previo)
			if (this.cmk == null) {
				if (oldPin == null || !verifyPin(oldPin)) {
					throw new IllegalStateException("PIN actual incorrecto o no verificado");
				}
			}
			byte[] salt = randomBytes(SALT_LEN);
			int iter = PBKDF2_ITERATIONS_DEFAULT;
			byte[] kek = deriveKey(newPin, salt, iter);
			byte[] iv = randomBytes(GCM_IV_LEN);
			byte[] wrap = aesGcmEncrypt(kek, this.cmk, iv);

			PREFS.putByteArray(K_PIN_SALT, salt);
			PREFS.putInt(K_PIN_ITER, iter);
			PREFS.putByteArray(K_CMK_WRAP, wrap);
			PREFS.putByteArray(K_CMK_WRAP_IV, iv);

			clearLockout();
		} finally {
			zero(oldPin);
			zero(newPin);
		}
	}

	// ==== Pregunta de seguridad ====

	/**
	 * Define/actualiza pregunta y respuesta de seguridad. Requiere CMK cargada (o
	 * sea, el usuario autenticado) para reenvolver CMK con la respuesta.
	 */
	public synchronized void setSecurityQuestion(String question, char[] answer) {
		if (question == null || question.isBlank())
			throw new IllegalArgumentException("Pregunta vacía");
		requireNonEmpty(answer);
		if (this.cmk == null)
			throw new IllegalStateException("Necesita desbloquear primero (PIN)");

		try {
			byte[] salt = randomBytes(SALT_LEN);
			byte[] kekQ = deriveKey(answer, salt, PBKDF2_ITERATIONS_DEFAULT);
			byte[] iv = randomBytes(GCM_IV_LEN);
			byte[] wrap = aesGcmEncrypt(kekQ, this.cmk, iv);

			PREFS.put(K_Q_TEXT, question);
			PREFS.putByteArray(K_Q_SALT, salt);
			PREFS.putByteArray(K_Q_WRAP, wrap);
			PREFS.putByteArray(K_Q_WRAP_IV, iv);
		} finally {
			zero(answer);
		}
	}

	/**
	 * Restablece PIN usando la respuesta de seguridad, SIN conocer el PIN anterior.
	 * Reenvuelve la CMK con el nuevo PIN si la respuesta es correcta.
	 */
	public synchronized boolean resetPinWithAnswer(char[] answer, char[] newPin) {
		requireNonEmpty(answer);
	    requireValidPin(newPin);
	    if (isLockedOut()) return false;

		byte[] salt = PREFS.getByteArray(K_Q_SALT, null);
		byte[] wrap = PREFS.getByteArray(K_Q_WRAP, null);
		byte[] iv = PREFS.getByteArray(K_Q_WRAP_IV, null);
		if (salt == null || wrap == null || iv == null) {
			zero(answer);
			zero(newPin);
			return false; // no configurada
		}

		try {
			byte[] kekQ = deriveKey(answer, salt, PBKDF2_ITERATIONS_DEFAULT);
			byte[] recoveredCmk = aesGcmDecrypt(kekQ, wrap, iv); // lanza AEADBadTag si mal

			// Tenemos CMK -> fijamos nuevo PIN
			this.cmk = recoveredCmk;
			clearLockout();
			// reenvuelve con nuevo PIN
			changePin(null, newPin);
			return true;
		} catch (AEADBadTagException bad) {
			int f = PREFS.getInt(K_FAILED, 0) + 1;
			PREFS.putInt(K_FAILED, f);
			if (f >= MAX_FAILED) {
				PREFS.putLong(K_LOCK_UNTIL, System.currentTimeMillis() + LOCKOUT_MS);
				PREFS.putInt(K_FAILED, 0);
			}
			return false;
		} catch (GeneralSecurityException e) {
			return false;
		} finally {
			zero(answer);
			zero(newPin);
		}
	}

	/**
	 * Restablece el PIN usando el código de recuperación (one-time). A la primera
	 * vez que funcione, borra la copia de recuperación.
	 */
	public synchronized boolean resetPinWithRecoveryCode(String code, char[] newPin) {
		if (code == null || code.isBlank())
			return false;
		requireValidPin(newPin);
		if (isLockedOut())
			return false;

		byte[] salt = PREFS.getByteArray(K_RC_SALT, null);
		byte[] wrap = PREFS.getByteArray(K_RC_WRAP, null);
		byte[] iv = PREFS.getByteArray(K_RC_WRAP_IV, null);
		if (salt == null || wrap == null || iv == null) {
			zero(newPin);
			return false; // no hay código activo
		}

		try {
			byte[] kekR = deriveKey(code.toCharArray(), salt, PBKDF2_ITERATIONS_DEFAULT);
			byte[] recoveredCmk = aesGcmDecrypt(kekR, wrap, iv);

			// éxito -> borra el recovery one-time
			PREFS.remove(K_RC_SALT);
			PREFS.remove(K_RC_WRAP);
			PREFS.remove(K_RC_WRAP_IV);

			this.cmk = recoveredCmk;
			clearLockout();
			changePin(null, newPin);
			return true;
		} catch (AEADBadTagException bad) {
			int f = PREFS.getInt(K_FAILED, 0) + 1;
			PREFS.putInt(K_FAILED, f);
			if (f >= MAX_FAILED) {
				PREFS.putLong(K_LOCK_UNTIL, System.currentTimeMillis() + LOCKOUT_MS);
				PREFS.putInt(K_FAILED, 0);
			}
			return false;
		} catch (GeneralSecurityException e) {
			return false;
		} finally {
			zero(newPin);
		}
	}
	
	// ==== Código de recuperación (one-time) ====

	/**
	 * Genera un código de recuperación y guarda una envoltura extra de la CMK
	 * usando ese código. Devuelve el código (muéstralo 1 vez al usuario para que lo
	 * guarde).
	 */
	public synchronized String generateRecoveryCode() {
		if (this.cmk == null)
			throw new IllegalStateException("Necesita desbloquear primero (PIN)");

		// Código legible: 4 bloques de 4 chars base32 (A-Z2-7) -> 19 chars con guiones
		byte[] raw = randomBytes(10); // 80 bits
		String code = toBase32Blocks(raw); // XXXX-XXXX-XXXX-XXXX

		byte[] salt = randomBytes(SALT_LEN);
		byte[] kekR = deriveKey(code.toCharArray(), salt, PBKDF2_ITERATIONS_DEFAULT);
		byte[] iv = randomBytes(GCM_IV_LEN);
		byte[] wrap = aesGcmEncrypt(kekR, this.cmk, iv);

		PREFS.putByteArray(K_RC_SALT, salt);
		PREFS.putByteArray(K_RC_WRAP, wrap);
		PREFS.putByteArray(K_RC_WRAP_IV, iv);

		return code;
	}


	// ==== Remember window ====

	public void startRememberWindow(Duration minutes) {
		long until = System.currentTimeMillis() + minutes.toMillis();
		PREFS.putLong(K_REMEMBER_UNTIL, until);
	}

	public boolean isRemembered() {
		return System.currentTimeMillis() < PREFS.getLong(K_REMEMBER_UNTIL, 0L);
	}

	// ==== Cifrado / Descifrado con CMK ====

	public Encrypted encrypt(String plaintext) {
		if (this.cmk == null)
			throw new IllegalStateException("CMK no cargada (pide PIN primero)");
		if (plaintext == null)
			plaintext = "";
		byte[] iv = randomBytes(GCM_IV_LEN);
		byte[] data = plaintext.getBytes(StandardCharsets.UTF_8);
		try {
			byte[] cipher = aesGcmEncrypt(this.cmk, data, iv);
			return new Encrypted(cipher, iv);
		} finally {
			Arrays.fill(data, (byte) 0);
		}
	}

	public String decrypt(byte[] cipher, byte[] iv) {
		if (this.cmk == null)
			throw new IllegalStateException("CMK no cargada (pide PIN primero)");
		try {
			byte[] plain = aesGcmDecrypt(this.cmk, cipher, iv);
			return new String(plain, StandardCharsets.UTF_8);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("No se pudo descifrar (¿datos corruptos?)", e);
		}
	}

	/**
	 * Asegura que la CMK está cargada (o recuerda PIN). Si no, invoca
	 * pinPrompt.get() para pedir PIN. Devuelve true si ha quedado desbloqueado,
	 * false si el usuario canceló o está en lockout.
	 */
	public boolean ensureUnlocked(Supplier<char[]> pinPrompt) {
		if (this.cmk != null) {
		    long now = System.currentTimeMillis();
		    long rememberUntil = PREFS.getLong(K_REMEMBER_UNTIL, 0L);
		    // Si hay "recordar", no caducamos; si no, caduca por TTL
		    boolean remembered = now < rememberUntil;
		    if (!remembered && now > cmkValidUntil) {
		        lockNow(); // limpia CMK y borra remember
		    } else {
		        return true;
		    }
		}

		// "Remember" sólo evita que re-pidamos mientras la app vive, pero si reinicias,
		// igualmente necesitas cargar CMK una vez introduciendo PIN.
		if (isLockedOut())
			return false;

		if (pinPrompt == null)
			return false;
		char[] pin = pinPrompt.get();
		if (pin == null || pin.length == 0)
			return false;
		return verifyPin(pin); // verifyPin limpia el lockout si acierta
	}

	// ==== Utils ====
	
	// Exponer intentos restantes y máximo para mensajes UI
	public int remainingAttempts() {
	    if (isLockedOut()) return 0;
	    int failed = PREFS.getInt(K_FAILED, 0);
	    int left = MAX_FAILED - failed;
	    return Math.max(0, left);
	}

	public int maxAttempts() {
	    return MAX_FAILED;
	}

	private static void requireNonEmpty(char[] pin) {
		if (pin == null || pin.length == 0)
			throw new IllegalArgumentException("PIN/clave vacía");
	}

	private static byte[] deriveKey(char[] password, byte[] salt, int iterations) {
		try {
			PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, PBKDF2_KEY_BITS);
			SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
			byte[] key = skf.generateSecret(spec).getEncoded();
			spec.clearPassword();
			return key;
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("PBKDF2 no disponible", e);
		} finally {
			zero(password);
		}
	}

	private static byte[] aesGcmEncrypt(byte[] key, byte[] plaintext, byte[] iv) {
		try {
			Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
			SecretKeySpec k = new SecretKeySpec(key, "AES");
			GCMParameterSpec g = new GCMParameterSpec(GCM_TAG_BITS, iv);
			c.init(Cipher.ENCRYPT_MODE, k, g);
			return c.doFinal(plaintext);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	private static byte[] aesGcmDecrypt(byte[] key, byte[] cipher, byte[] iv) throws GeneralSecurityException {
		Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
		SecretKeySpec k = new SecretKeySpec(key, "AES");
		GCMParameterSpec g = new GCMParameterSpec(GCM_TAG_BITS, iv);
		c.init(Cipher.DECRYPT_MODE, k, g);
		return c.doFinal(cipher);
	}

	private static byte[] randomBytes(int len) {
		byte[] out = new byte[len];
		RNG.nextBytes(out);
		return out;
	}

	private static void zero(char[] a) {
		if (a != null)
			Arrays.fill(a, '\0');
	}

	// Base32 crockford-like (A-Z2-7) con guiones cada 4 chars (sólo para mostrar
	// códigos legibles)
	private static final char[] B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

	private static String toBase32Blocks(byte[] data) {
		// simple encoder sin padding
		int outputLen = (int) Math.ceil(data.length * 8.0 / 5.0);
		StringBuilder sb = new StringBuilder(outputLen + outputLen / 4);
		int buffer = 0, bitsLeft = 0, count = 0;
		for (byte b : data) {
			buffer = (buffer << 8) | (b & 0xFF);
			bitsLeft += 8;
			while (bitsLeft >= 5) {
				int idx = (buffer >> (bitsLeft - 5)) & 0x1F;
				bitsLeft -= 5;
				if (count > 0 && count % 4 == 0)
					sb.append('-');
				sb.append(B32[idx]);
				count++;
			}
		}
		if (bitsLeft > 0) {
			int idx = (buffer << (5 - bitsLeft)) & 0x1F;
			if (count > 0 && count % 4 == 0)
				sb.append('-');
			sb.append(B32[idx]);
		}
		// genera 4 bloques de 4 (o lo que salga según data); para 10 bytes salen 16
		// chars -> 4 bloques
		return sb.toString();
	}
}
