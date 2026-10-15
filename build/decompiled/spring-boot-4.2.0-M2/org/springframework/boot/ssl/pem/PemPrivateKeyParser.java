package org.springframework.boot.ssl.pem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

final class PemPrivateKeyParser {
   private static final String PKCS1_RSA_HEADER = "-+BEGIN\\s+RSA\\s+PRIVATE\\s+KEY[^-]*-+(?:\\s|\\r|\\n)+";
   private static final String PKCS1_RSA_FOOTER = "-+END\\s+RSA\\s+PRIVATE\\s+KEY[^-]*-+";
   private static final String PKCS8_HEADER = "-+BEGIN\\s+PRIVATE\\s+KEY[^-]*-+(?:\\s|\\r|\\n)+";
   private static final String PKCS8_FOOTER = "-+END\\s+PRIVATE\\s+KEY[^-]*-+";
   private static final String PKCS8_ENCRYPTED_HEADER = "-+BEGIN\\s+ENCRYPTED\\s+PRIVATE\\s+KEY[^-]*-+(?:\\s|\\r|\\n)+";
   private static final String PKCS8_ENCRYPTED_FOOTER = "-+END\\s+ENCRYPTED\\s+PRIVATE\\s+KEY[^-]*-+";
   private static final String SEC1_EC_HEADER = "-+BEGIN\\s+EC\\s+PRIVATE\\s+KEY[^-]*-+(?:\\s|\\r|\\n)+";
   private static final String SEC1_EC_FOOTER = "-+END\\s+EC\\s+PRIVATE\\s+KEY[^-]*-+";
   private static final String BASE64_TEXT = "([a-z0-9+/=\\r\\n]+)";
   public static final int BASE64_TEXT_GROUP = 1;
   private static final PemPrivateKeyParser.EncodedOid RSA_ALGORITHM = PemPrivateKeyParser.EncodedOid.OID_1_2_840_113549_1_1_1;
   private static final PemPrivateKeyParser.EncodedOid ELLIPTIC_CURVE_ALGORITHM = PemPrivateKeyParser.EncodedOid.OID_1_2_840_10045_2_1;
   private static final PemPrivateKeyParser.EncodedOid ELLIPTIC_CURVE_384_BIT = PemPrivateKeyParser.EncodedOid.OID_1_3_132_0_34;
   private static final Map<PemPrivateKeyParser.EncodedOid, String> ALGORITHMS;
   private static final List<PemPrivateKeyParser.PemParser> PEM_PARSERS;

   private PemPrivateKeyParser() {
   }

   private static PKCS8EncodedKeySpec createKeySpecForPkcs1Rsa(byte[] bytes, @Nullable String password) {
      return createKeySpecForAlgorithm(bytes, RSA_ALGORITHM, null);
   }

   private static PKCS8EncodedKeySpec createKeySpecForSec1Ec(byte[] bytes, @Nullable String password) {
      PemPrivateKeyParser.DerElement ecPrivateKey = PemPrivateKeyParser.DerElement.of(bytes);
      Assert.state(ecPrivateKey != null, "Unable to find private key");
      Assert.state(
         ecPrivateKey.isType(PemPrivateKeyParser.DerElement.ValueType.ENCODED, PemPrivateKeyParser.DerElement.TagType.SEQUENCE),
         "Key spec should be an ASN.1 encoded sequence"
      );
      PemPrivateKeyParser.DerElement version = PemPrivateKeyParser.DerElement.of(ecPrivateKey.getContents());
      Assert.state(
         version != null && version.isType(PemPrivateKeyParser.DerElement.ValueType.PRIMITIVE, PemPrivateKeyParser.DerElement.TagType.INTEGER),
         "Key spec should start with version"
      );
      Assert.state(version.getContents().remaining() == 1 && version.getContents().get() == 1, "Key spec version must be 1");
      PemPrivateKeyParser.DerElement privateKey = PemPrivateKeyParser.DerElement.of(ecPrivateKey.getContents());
      Assert.state(
         privateKey != null && privateKey.isType(PemPrivateKeyParser.DerElement.ValueType.PRIMITIVE, PemPrivateKeyParser.DerElement.TagType.OCTET_STRING),
         "Key spec should contain private key"
      );
      PemPrivateKeyParser.DerElement parameters = PemPrivateKeyParser.DerElement.of(ecPrivateKey.getContents());
      return createKeySpecForAlgorithm(bytes, ELLIPTIC_CURVE_ALGORITHM, getEcParameters(parameters));
   }

   private static PemPrivateKeyParser.EncodedOid getEcParameters(PemPrivateKeyParser.@Nullable DerElement parameters) {
      if (parameters == null) {
         return ELLIPTIC_CURVE_384_BIT;
      }

      Assert.state(parameters.isType(PemPrivateKeyParser.DerElement.ValueType.ENCODED), "Key spec should contain encoded parameters");
      PemPrivateKeyParser.DerElement contents = PemPrivateKeyParser.DerElement.of(parameters.getContents());
      Assert.state(
         contents != null && contents.isType(PemPrivateKeyParser.DerElement.ValueType.PRIMITIVE, PemPrivateKeyParser.DerElement.TagType.OBJECT_IDENTIFIER),
         "Key spec parameters should contain object identifier"
      );
      return PemPrivateKeyParser.EncodedOid.of(contents);
   }

   private static PKCS8EncodedKeySpec createKeySpecForAlgorithm(
      byte[] bytes, PemPrivateKeyParser.EncodedOid algorithm, PemPrivateKeyParser.@Nullable EncodedOid parameters
   ) {
      try {
         PemPrivateKeyParser.DerEncoder encoder = new PemPrivateKeyParser.DerEncoder();
         encoder.integer(0);
         PemPrivateKeyParser.DerEncoder algorithmIdentifier = new PemPrivateKeyParser.DerEncoder();
         algorithmIdentifier.objectIdentifier(algorithm);
         algorithmIdentifier.objectIdentifier(parameters);
         encoder.sequence(algorithmIdentifier.toByteArray());
         encoder.octetString(bytes);
         return new PKCS8EncodedKeySpec(encoder.toSequence());
      } catch (IOException ex) {
         throw new IllegalStateException(ex);
      }
   }

   private static PKCS8EncodedKeySpec createKeySpecForPkcs8(byte[] bytes, @Nullable String password) {
      PemPrivateKeyParser.DerElement ecPrivateKey = PemPrivateKeyParser.DerElement.of(bytes);
      Assert.state(ecPrivateKey != null, "Unable to find private key");
      Assert.state(
         ecPrivateKey.isType(PemPrivateKeyParser.DerElement.ValueType.ENCODED, PemPrivateKeyParser.DerElement.TagType.SEQUENCE),
         "Key spec should be an ASN.1 encoded sequence"
      );
      PemPrivateKeyParser.DerElement version = PemPrivateKeyParser.DerElement.of(ecPrivateKey.getContents());
      Assert.state(
         version != null && version.isType(PemPrivateKeyParser.DerElement.ValueType.PRIMITIVE, PemPrivateKeyParser.DerElement.TagType.INTEGER),
         "Key spec should start with version"
      );
      PemPrivateKeyParser.DerElement sequence = PemPrivateKeyParser.DerElement.of(ecPrivateKey.getContents());
      Assert.state(
         sequence != null && sequence.isType(PemPrivateKeyParser.DerElement.ValueType.ENCODED, PemPrivateKeyParser.DerElement.TagType.SEQUENCE),
         "Key spec should contain private key"
      );
      PemPrivateKeyParser.DerElement algorithmId = PemPrivateKeyParser.DerElement.of(sequence.getContents());
      Assert.state(
         algorithmId != null
            && algorithmId.isType(PemPrivateKeyParser.DerElement.ValueType.PRIMITIVE, PemPrivateKeyParser.DerElement.TagType.OBJECT_IDENTIFIER),
         "Key spec container object identifier"
      );
      String algorithmName = ALGORITHMS.get(PemPrivateKeyParser.EncodedOid.of(algorithmId));
      return algorithmName != null ? new PKCS8EncodedKeySpec(bytes, algorithmName) : new PKCS8EncodedKeySpec(bytes);
   }

   private static PKCS8EncodedKeySpec createKeySpecForPkcs8Encrypted(byte[] bytes, @Nullable String password) {
      return PemPrivateKeyParser.Pkcs8PrivateKeyDecryptor.decrypt(bytes, password);
   }

   static @Nullable PrivateKey parse(String text) {
      return parse(text, null);
   }

   static @Nullable PrivateKey parse(@Nullable String text, @Nullable String password) {
      if (text == null) {
         return null;
      }

      try {
         for (PemPrivateKeyParser.PemParser pemParser : PEM_PARSERS) {
            PrivateKey privateKey = pemParser.parse(text, password);
            if (privateKey != null) {
               return privateKey;
            }
         }
      } catch (Exception ex) {
         throw new IllegalStateException("Error loading private key file: " + ex.getMessage(), ex);
      }

      throw new IllegalStateException("Missing private key or unrecognized format");
   }

   static {
      Map<PemPrivateKeyParser.EncodedOid, String> algorithms = new HashMap<>();
      algorithms.put(PemPrivateKeyParser.EncodedOid.OID_1_2_840_113549_1_1_1, "RSA");
      algorithms.put(PemPrivateKeyParser.EncodedOid.OID_1_2_840_113549_1_1_10, "RSA");
      algorithms.put(PemPrivateKeyParser.EncodedOid.OID_1_2_840_10040_4_1, "DSA");
      algorithms.put(PemPrivateKeyParser.EncodedOid.OID_1_3_101_110, "XDH");
      algorithms.put(PemPrivateKeyParser.EncodedOid.OID_1_3_101_111, "XDH");
      algorithms.put(PemPrivateKeyParser.EncodedOid.OID_1_3_101_112, "EdDSA");
      algorithms.put(PemPrivateKeyParser.EncodedOid.OID_1_3_101_113, "EdDSA");
      algorithms.put(PemPrivateKeyParser.EncodedOid.OID_1_2_840_10045_2_1, "EC");
      ALGORITHMS = Collections.unmodifiableMap(algorithms);
      List<PemPrivateKeyParser.PemParser> parsers = new ArrayList<>();
      parsers.add(
         new PemPrivateKeyParser.PemParser(
            "-+BEGIN\\s+RSA\\s+PRIVATE\\s+KEY[^-]*-+(?:\\s|\\r|\\n)+",
            "-+END\\s+RSA\\s+PRIVATE\\s+KEY[^-]*-+",
            PemPrivateKeyParser::createKeySpecForPkcs1Rsa,
            "RSA"
         )
      );
      parsers.add(
         new PemPrivateKeyParser.PemParser(
            "-+BEGIN\\s+EC\\s+PRIVATE\\s+KEY[^-]*-+(?:\\s|\\r|\\n)+", "-+END\\s+EC\\s+PRIVATE\\s+KEY[^-]*-+", PemPrivateKeyParser::createKeySpecForSec1Ec, "EC"
         )
      );
      parsers.add(
         new PemPrivateKeyParser.PemParser(
            "-+BEGIN\\s+PRIVATE\\s+KEY[^-]*-+(?:\\s|\\r|\\n)+",
            "-+END\\s+PRIVATE\\s+KEY[^-]*-+",
            PemPrivateKeyParser::createKeySpecForPkcs8,
            "RSA",
            "RSASSA-PSS",
            "EC",
            "DSA",
            "EdDSA",
            "XDH"
         )
      );
      parsers.add(
         new PemPrivateKeyParser.PemParser(
            "-+BEGIN\\s+ENCRYPTED\\s+PRIVATE\\s+KEY[^-]*-+(?:\\s|\\r|\\n)+",
            "-+END\\s+ENCRYPTED\\s+PRIVATE\\s+KEY[^-]*-+",
            PemPrivateKeyParser::createKeySpecForPkcs8Encrypted,
            "RSA",
            "RSASSA-PSS",
            "EC",
            "DSA",
            "EdDSA",
            "XDH"
         )
      );
      PEM_PARSERS = Collections.unmodifiableList(parsers);
   }

   static final class DerElement {
      private final PemPrivateKeyParser.DerElement.ValueType valueType;
      private final long tagType;
      private final ByteBuffer contents;

      private DerElement(ByteBuffer bytes) {
         byte b = bytes.get();
         this.valueType = (b & 32) == 0 ? PemPrivateKeyParser.DerElement.ValueType.PRIMITIVE : PemPrivateKeyParser.DerElement.ValueType.ENCODED;
         this.tagType = this.decodeTagType(b, bytes);
         int length = this.decodeLength(bytes);
         bytes.limit(bytes.position() + length);
         this.contents = bytes.slice();
         bytes.limit(bytes.capacity());
         bytes.position(bytes.position() + length);
      }

      private long decodeTagType(byte b, ByteBuffer bytes) {
         long tagType = b & 31;
         if (tagType != 31L) {
            return tagType;
         }

         tagType = 0L;

         for (byte var5 = bytes.get(); (var5 & 128) != 0; var5 = bytes.get()) {
            tagType <<= 7;
            tagType |= var5 & 127;
         }

         return tagType;
      }

      private int decodeLength(ByteBuffer bytes) {
         byte b = bytes.get();
         if ((b & 128) == 0) {
            return b & 127;
         }

         int numberOfLengthBytes = b & 127;
         Assert.state(numberOfLengthBytes != 0, "Infinite length encoding is not supported");
         Assert.state(numberOfLengthBytes != 127, "Reserved length encoding is not supported");
         Assert.state(numberOfLengthBytes <= 4, "Length overflow");
         int length = 0;

         for (int i = 0; i < numberOfLengthBytes; i++) {
            length <<= 8;
            length |= bytes.get() & 255;
         }

         return length;
      }

      boolean isType(PemPrivateKeyParser.DerElement.ValueType valueType) {
         return this.valueType == valueType;
      }

      boolean isType(PemPrivateKeyParser.DerElement.ValueType valueType, PemPrivateKeyParser.DerElement.TagType tagType) {
         return this.valueType == valueType && this.tagType == tagType.getNumber();
      }

      ByteBuffer getContents() {
         return this.contents;
      }

      static PemPrivateKeyParser.@Nullable DerElement of(byte[] bytes) {
         return of(ByteBuffer.wrap(bytes));
      }

      static PemPrivateKeyParser.@Nullable DerElement of(ByteBuffer bytes) {
         return bytes.remaining() > 0 ? new PemPrivateKeyParser.DerElement(bytes) : null;
      }

      enum TagType {
         INTEGER(2),
         OCTET_STRING(4),
         OBJECT_IDENTIFIER(6),
         SEQUENCE(16);

         private final int number;

         TagType(int number) {
            this.number = number;
         }

         int getNumber() {
            return this.number;
         }
      }

      enum ValueType {
         PRIMITIVE,
         ENCODED;
      }
   }

   static class DerEncoder {
      private final ByteArrayOutputStream stream = new ByteArrayOutputStream();

      void objectIdentifier(PemPrivateKeyParser.@Nullable EncodedOid encodedOid) throws IOException {
         int code = encodedOid != null ? 6 : 5;
         this.codeLengthBytes(code, encodedOid != null ? encodedOid.toByteArray() : null);
      }

      void integer(int... encodedInteger) throws IOException {
         this.codeLengthBytes(2, bytes(encodedInteger));
      }

      void octetString(byte[] bytes) throws IOException {
         this.codeLengthBytes(4, bytes);
      }

      void sequence(byte[] bytes) throws IOException {
         this.codeLengthBytes(48, bytes);
      }

      void codeLengthBytes(int code, byte @Nullable [] bytes) throws IOException {
         this.stream.write(code);
         int length = bytes != null ? bytes.length : 0;
         if (length <= 127) {
            this.stream.write(length & 0xFF);
         } else {
            ByteArrayOutputStream lengthStream = new ByteArrayOutputStream();

            while (length != 0) {
               lengthStream.write(length & 0xFF);
               length >>= 8;
            }

            byte[] lengthBytes = lengthStream.toByteArray();
            this.stream.write(128 | lengthBytes.length);

            for (int i = lengthBytes.length - 1; i >= 0; i--) {
               this.stream.write(lengthBytes[i]);
            }
         }

         if (bytes != null) {
            this.stream.write(bytes);
         }
      }

      private static byte @Nullable [] bytes(int @Nullable ... elements) {
         if (elements == null) {
            return null;
         }

         byte[] result = new byte[elements.length];

         for (int i = 0; i < elements.length; i++) {
            result[i] = (byte)elements[i];
         }

         return result;
      }

      byte[] toSequence() throws IOException {
         PemPrivateKeyParser.DerEncoder sequenceEncoder = new PemPrivateKeyParser.DerEncoder();
         sequenceEncoder.sequence(this.toByteArray());
         return sequenceEncoder.toByteArray();
      }

      byte[] toByteArray() {
         return this.stream.toByteArray();
      }
   }

   static final class EncodedOid {
      static final PemPrivateKeyParser.EncodedOid OID_1_2_840_10040_4_1 = of("2a8648ce380401");
      static final PemPrivateKeyParser.EncodedOid OID_1_2_840_113549_1_1_1 = of("2A864886F70D010101");
      static final PemPrivateKeyParser.EncodedOid OID_1_2_840_113549_1_1_10 = of("2a864886f70d01010a");
      static final PemPrivateKeyParser.EncodedOid OID_1_3_101_110 = of("2b656e");
      static final PemPrivateKeyParser.EncodedOid OID_1_3_101_111 = of("2b656f");
      static final PemPrivateKeyParser.EncodedOid OID_1_3_101_112 = of("2b6570");
      static final PemPrivateKeyParser.EncodedOid OID_1_3_101_113 = of("2b6571");
      static final PemPrivateKeyParser.EncodedOid OID_1_2_840_10045_2_1 = of("2a8648ce3d0201");
      static final PemPrivateKeyParser.EncodedOid OID_1_3_132_0_34 = of("2b81040022");
      private final byte[] value;

      private EncodedOid(byte[] value) {
         this.value = value;
      }

      byte[] toByteArray() {
         return (byte[])this.value.clone();
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         } else {
            return obj != null && this.getClass() == obj.getClass() ? Arrays.equals(this.value, ((PemPrivateKeyParser.EncodedOid)obj).value) : false;
         }
      }

      @Override
      public int hashCode() {
         return Arrays.hashCode(this.value);
      }

      static PemPrivateKeyParser.EncodedOid of(String hexString) {
         return of(HexFormat.of().parseHex(hexString));
      }

      static PemPrivateKeyParser.EncodedOid of(PemPrivateKeyParser.DerElement derElement) {
         return of(derElement.getContents());
      }

      static PemPrivateKeyParser.EncodedOid of(ByteBuffer byteBuffer) {
         return of(byteBuffer.array(), byteBuffer.arrayOffset() + byteBuffer.position(), byteBuffer.remaining());
      }

      static PemPrivateKeyParser.EncodedOid of(byte[] bytes) {
         return of(bytes, 0, bytes.length);
      }

      static PemPrivateKeyParser.EncodedOid of(byte[] bytes, int off, int len) {
         byte[] value = new byte[len];
         System.arraycopy(bytes, off, value, 0, len);
         return new PemPrivateKeyParser.EncodedOid(value);
      }
   }

   private static class PemParser {
      private final Pattern pattern;
      private final BiFunction<byte[], @Nullable String, PKCS8EncodedKeySpec> keySpecFactory;
      private final String[] algorithms;

      PemParser(String header, String footer, BiFunction<byte[], @Nullable String, PKCS8EncodedKeySpec> keySpecFactory, String... algorithms) {
         this.pattern = Pattern.compile(header + "([a-z0-9+/=\\r\\n]+)" + footer, 2);
         this.keySpecFactory = keySpecFactory;
         this.algorithms = algorithms;
      }

      @Nullable PrivateKey parse(String text, @Nullable String password) {
         Matcher matcher = this.pattern.matcher(text);
         return !matcher.find() ? null : this.parse(decodeBase64(matcher.group(1)), password);
      }

      private static byte[] decodeBase64(String content) {
         byte[] contentBytes = content.replace("\r", "").replace("\n", "").getBytes();
         return Base64.getDecoder().decode(contentBytes);
      }

      private @Nullable PrivateKey parse(byte[] bytes, @Nullable String password) {
         PKCS8EncodedKeySpec keySpec = this.keySpecFactory.apply(bytes, password);
         if (keySpec.getAlgorithm() != null) {
            try {
               KeyFactory keyFactory = KeyFactory.getInstance(keySpec.getAlgorithm());
               return keyFactory.generatePrivate(keySpec);
            } catch (InvalidKeySpecException | NoSuchAlgorithmException var10) {
            }
         }

         for (String algorithm : this.algorithms) {
            try {
               KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
               return keyFactory.generatePrivate(keySpec);
            } catch (InvalidKeySpecException | NoSuchAlgorithmException var9) {
            }
         }

         return null;
      }
   }

   static class Pkcs8PrivateKeyDecryptor {
      public static final String PBES2_ALGORITHM = "PBES2";

      static PKCS8EncodedKeySpec decrypt(byte[] bytes, @Nullable String password) {
         Assert.state(password != null, "Password is required for an encrypted private key");

         try {
            EncryptedPrivateKeyInfo keyInfo = new EncryptedPrivateKeyInfo(bytes);
            AlgorithmParameters algorithmParameters = keyInfo.getAlgParameters();
            String encryptionAlgorithm = getEncryptionAlgorithm(algorithmParameters, keyInfo.getAlgName());
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(encryptionAlgorithm);
            SecretKey key = keyFactory.generateSecret(new PBEKeySpec(password.toCharArray()));
            Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
            cipher.init(2, key, algorithmParameters);
            return keyInfo.getKeySpec(cipher);
         } catch (IOException | GeneralSecurityException ex) {
            throw new IllegalArgumentException("Error decrypting private key", ex);
         }
      }

      private static String getEncryptionAlgorithm(@Nullable AlgorithmParameters algParameters, String algName) {
         return algParameters != null && "PBES2".equals(algName) ? algParameters.toString() : algName;
      }
   }
}
