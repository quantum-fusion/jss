/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.jss.pkcs11;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

import javax.crypto.spec.RC2ParameterSpec;

import org.mozilla.jss.crypto.Algorithm;
import org.mozilla.jss.crypto.EncryptionAlgorithm;
import org.mozilla.jss.crypto.HMACAlgorithm;
import org.mozilla.jss.crypto.IVParameterSpec;
import org.mozilla.jss.crypto.KeyPairAlgorithm;
import org.mozilla.jss.crypto.KeyWrapAlgorithm;
import org.mozilla.jss.crypto.KeyWrapper;
import org.mozilla.jss.crypto.PrivateKey;
import org.mozilla.jss.crypto.SymmetricKey;
import org.mozilla.jss.crypto.TokenException;
import org.mozilla.jss.util.Assert;

final class PK11KeyWrapper implements KeyWrapper {

    private PK11Token token;
    private KeyWrapAlgorithm algorithm;
    private int state=UNINITIALIZED;
    private AlgorithmParameterSpec parameters=null;
    private SymmetricKey symKey=null;
    private PrivateKey privKey=null;
    private PublicKey pubKey=null;
    private byte[] IV=null;

    // states
    private static final int UNINITIALIZED=0;
    private static final int WRAP=1;
    private static final int UNWRAP=2;

    private PK11KeyWrapper() { }

    PK11KeyWrapper(PK11Token token, KeyWrapAlgorithm algorithm) {
        this.token = token;
        this.algorithm = algorithm;
    }

    public void initWrap(SymmetricKey wrappingKey,
                    AlgorithmParameterSpec parameters)
        throws InvalidKeyException, InvalidAlgorithmParameterException
    {
        initWrap(parameters);
        checkWrapper(wrappingKey);
        this.symKey = wrappingKey;
    }

    public void initWrap(PublicKey wrappingKey,
                            AlgorithmParameterSpec parameters)
            throws InvalidKeyException, InvalidAlgorithmParameterException
    {
        initWrap(parameters);
        checkWrapper(wrappingKey);
        this.pubKey = wrappingKey;
    }

    public void initWrap()
            throws InvalidKeyException, InvalidAlgorithmParameterException
    {
        if( algorithm != KeyWrapAlgorithm.PLAINTEXT ) {
            throw new InvalidKeyException(algorithm + " requires a key");
        }
        reset();
        state = WRAP;
    }

    /**
     * Does everything that is key-independent for initializing a wrap.
     */
    private void initWrap(AlgorithmParameterSpec parameters)
        throws InvalidAlgorithmParameterException
    {
        reset();

        checkParams(parameters);

        this.parameters = parameters;
        state = WRAP;
    }

    public void initUnwrap(PrivateKey unwrappingKey,
                    AlgorithmParameterSpec parameters)
        throws InvalidKeyException, InvalidAlgorithmParameterException
    {
        initUnwrap(parameters);
        checkWrapper(unwrappingKey);
        this.privKey = unwrappingKey;
    }

    public void initUnwrap(SymmetricKey unwrappingKey,
                    AlgorithmParameterSpec parameters)
        throws InvalidKeyException, InvalidAlgorithmParameterException
    {
        initUnwrap(parameters);
        checkWrapper(unwrappingKey);
        this.symKey = unwrappingKey;
    }

    public void initUnwrap()
        throws InvalidKeyException, InvalidAlgorithmParameterException
    {
        if( algorithm != KeyWrapAlgorithm.PLAINTEXT ) {
            throw new InvalidKeyException(algorithm + " requires a key");
        }
        reset();
        state = UNWRAP;
    }

    /**
     * Does the key-independent parts of initializing an unwrap.
     */
    private void initUnwrap(AlgorithmParameterSpec parameters)
        throws InvalidAlgorithmParameterException
    {
        reset();

        checkParams(parameters);

        this.parameters = parameters;
        state = UNWRAP;
    }

    /**
     * Makes sure the key is right for the algorithm.
     */
    private void checkWrapper(PublicKey key) throws InvalidKeyException {
        if( key==null ) {
            throw new InvalidKeyException("Key is null");
        }
        if( ! (key instanceof PK11PubKey) ) {
            throw new InvalidKeyException("Key is not a PKCS #11 key");
        }
        try {
            KeyType type = KeyType.getKeyTypeFromAlgorithm(algorithm);
            if( (type == KeyType.RSA && !(key instanceof RSAPublicKey)) ||
		// requires JAVA 1.5
                // (type == KeyType.EC && !(key instanceof ECPublicKey)) ||
                (type == KeyType.DSA && !(key instanceof DSAPublicKey)) ) {
                throw new InvalidKeyException("Key is not the right type for "+
                    "this algorithm");
            }
        } catch( NoSuchAlgorithmException e ) {
            throw new RuntimeException("Unable to find algorithm from key type: " + e.getMessage(), e);
        }
    }

    /**
     * Makes sure the key lives on the token and is right for the algorithm.
     */
    private void checkWrapper(SymmetricKey key)
        throws InvalidKeyException
    {
        if( key==null ) {
            throw new InvalidKeyException("Key is null");
        }
        try {
            if( ! key.getOwningToken().equals(token) ) {
                throw new InvalidKeyException("Key does not reside on the current token: key owning token="+
                    key.getOwningToken().getName());
            }
            if( ! (key instanceof PK11SymKey) ) {
                throw new InvalidKeyException("Key is not a PKCS #11 key");
            }
            if( ((PK11SymKey)key).getKeyType() !=
                KeyType.getKeyTypeFromAlgorithm(algorithm) ) {
                    throw new InvalidKeyException("Key is not the right type for"+
                    " this algorithm");
            }
        } catch( NoSuchAlgorithmException e ) {
            throw new RuntimeException("Unknown algorithm: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Unable to check wrapper: " + e.getMessage(), e);
        }
    }

    /**
     * Makes sure the key is on the token and is right for the algorithm.
     */
    private void checkWrapper(PrivateKey key)
        throws InvalidKeyException
    {
        if( key==null ) {
            throw new InvalidKeyException("Key is null");
        }
        if( ! key.getOwningToken().equals(token) ) {
            throw new InvalidKeyException("Key does not reside on the "+
                "current token");
        }
        if( ! (key instanceof PK11PrivKey) ) {
            throw new InvalidKeyException("Key is not a PKCS #11 key");
        }
        try {
            if( ((PK11PrivKey)key).getKeyType() !=
                    KeyType.getKeyTypeFromAlgorithm(algorithm) ) {
                throw new InvalidKeyException("Key is not the right type for"+
                    " this algorithm");
            }
        } catch( NoSuchAlgorithmException e ) {
            throw new RuntimeException("Unknown algorithm: " + e.getMessage(), e);
        }
    }

    private void checkParams(AlgorithmParameterSpec params)
        throws InvalidAlgorithmParameterException
    {
        if( ! algorithm.isValidParameterObject(params) ) {
            String name = "null";
            if( params != null ) {
                name = params.getClass().getName();
            }
            throw new InvalidAlgorithmParameterException(
                algorithm + " cannot use a " + name + " parameter");
        }
        if( params instanceof IVParameterSpec ) {
            IV = ((IVParameterSpec)params).getIV();
        } else if( params instanceof javax.crypto.spec.IvParameterSpec ) {
            IV = ((javax.crypto.spec.IvParameterSpec)params).getIV();
        } else if( params instanceof RC2ParameterSpec ) {
            IV = ((RC2ParameterSpec)params).getIV();
        }
    }

    public byte[]
    wrap(PrivateKey toBeWrapped)
        throws InvalidKeyException, IllegalStateException, TokenException
    {
        if( state != WRAP ) {
            throw new IllegalStateException();
        }
        if( algorithm == KeyWrapAlgorithm.PLAINTEXT ) {
            throw new InvalidKeyException(
                "plaintext wrapping not supported");
        }

        checkWrappee(toBeWrapped);

        if( symKey != null ) {
            Assert._assert( privKey==null && pubKey==null );
            return nativeWrapPrivWithSym(token, toBeWrapped, symKey, algorithm,
                IV);
        } else {
            throw new InvalidKeyException(
                "Wrapping a private key with a public key is not supported");
            /*
            Assert._assert( pubKey!=null && privKey==null && symKey==null );
            return nativeWrapPrivWithPub(token, toBeWrapped, pubKey, algorithm,
                    IV);
            */
        }
    }

    public byte[]
    wrap(SymmetricKey toBeWrapped)
        throws InvalidKeyException, IllegalStateException, TokenException
    {
        if( state != WRAP ) {
            throw new IllegalStateException();
        }
        if( algorithm == KeyWrapAlgorithm.PLAINTEXT ) {
            throw new InvalidKeyException("plaintext wrapping not supported");
        }

        checkWrappee(toBeWrapped);

        if( symKey != null ) {
            Assert._assert( privKey==null && pubKey==null );
            return nativeWrapSymWithSym(token, toBeWrapped, symKey, algorithm,
                        IV);
        } else {
            Assert._assert( pubKey!=null && privKey==null && symKey==null );
            return nativeWrapSymWithPub(token, toBeWrapped, pubKey, algorithm,
                        IV);
        }
    }

    /**
     * Makes sure the key lives on the right token.
     */
    private void
    checkWrappee(SymmetricKey symKey) throws InvalidKeyException {
        if( symKey == null ) {
            throw new InvalidKeyException("key to be wrapped is null");
        }
        if( ! (symKey instanceof PK11SymKey) ) {
            throw new InvalidKeyException("key to be wrapped is not a "+
                "PKCS #11 key");
        }
/* NSS is capable of moving keys appropriately,
   so this call is prematurely bailing
        if( ! symKey.getOwningToken().equals(token) ) {
            throw new InvalidKeyException("key to be wrapped does not live"+
                " on the same token as the wrapping key");
        }
*/
    }

    /**
     * Makes sure the key lives on the right token.
     */
    private void
    checkWrappee(PrivateKey privKey) throws InvalidKeyException {
        if( privKey == null ) {
            throw new InvalidKeyException("key to be wrapped is null");
        }
        if( ! (privKey instanceof PK11PrivKey) ) {
            throw new InvalidKeyException("key to be wrapped is not a "+
                "PKCS #11 key");
        }
/* NSS is capable of moving keys appropriately,
   so this call is prematurely bailing
        if( ! privKey.getOwningToken().equals(token) ) {
            throw new InvalidKeyException("key to be wrapped does not live"+
                " on the same token as the wrapping key");
        }
*/
    }

    /**
     * Wrap a symmetric with a symmetric
     */
    private static native byte[]
    nativeWrapSymWithSym(PK11Token token, SymmetricKey toBeWrapped,
        SymmetricKey wrappingKey, KeyWrapAlgorithm alg, byte[] IV)
            throws TokenException;

    /**
     * Wrap a symmetric with a public
     */
    private static native byte[]
    nativeWrapSymWithPub(PK11Token token, SymmetricKey toBeWrapped,
        PublicKey wrappingKey, KeyWrapAlgorithm alg, byte[] IV)
            throws TokenException;

    /**
     * Wrap a private with a symmetric
     */
    private static native byte[]
    nativeWrapPrivWithSym(PK11Token token, PrivateKey toBeWrapped,
        SymmetricKey wrappingKey, KeyWrapAlgorithm alg, byte[] IV)
            throws TokenException;

    /**
     * Wrap a private with a public.
     * NOTE: This operation is not supported by the security library.
    private static native byte[]
    nativeWrapPrivWithPub(PK11Token token, PrivateKey toBeWrapped,
        PublicKey wrappingKey,
        KeyWrapAlgorithm alg, byte[] IV)
            throws TokenException;
     */

    /**
     * Unwraps a private key, creating a permanent private key object.
     * A permanent private key object resides on a token until it is
     * explicitly deleted from the token.
     */
    public PrivateKey
    unwrapPrivate(byte[] wrapped, PrivateKey.Type type, PublicKey publicKey)
        throws TokenException, InvalidKeyException, IllegalStateException
    {
        return baseUnwrapPrivate(wrapped, type, publicKey, false);
    }

    /**
     * Unwraps a private key, creating a temporary private key object.
     * A temporary
     * private key is one that does not permanently reside on a token.
     * As soon as it is garbage-collected, it is gone forever.
     */
    public PrivateKey
    unwrapTemporaryPrivate(byte[] wrapped, PrivateKey.Type type,
        PublicKey publicKey)
        throws TokenException, InvalidKeyException, IllegalStateException
    {
        return baseUnwrapPrivate(wrapped, type, publicKey, true);
    }

    private PrivateKey
    baseUnwrapPrivate(byte[] wrapped, PrivateKey.Type type,
            PublicKey publicKey, boolean temporary)
        throws TokenException, InvalidKeyException, IllegalStateException
    {
        if( state != UNWRAP ) {
            throw new IllegalStateException();
        }
        if( algorithm == KeyWrapAlgorithm.PLAINTEXT ) {
            throw new TokenException("plaintext unwrapping of private keys " +
                "is not supported");
        }

        byte[] publicValue = extractPublicValue(publicKey, type);
        /* If first byte is null, omit it.
         * It can be null due to how BigInteger.toByteArray() is specified. */
        if (publicValue.length > 0 && publicValue[0] == 0) {
            publicValue = Arrays.copyOfRange(publicValue, 1, publicValue.length);
        }

        if( symKey != null ) {
            Assert._assert(pubKey==null && privKey==null);
            PrivateKey importedKey = nativeUnwrapPrivWithSym(
                token, symKey, wrapped, algorithm, algFromType(type),
                publicValue, IV, temporary);

            if (!temporary
                    && publicKey instanceof org.mozilla.jss.pkcs11.PK11PubKey) {
                try {
                    token.importPublicKey(
                        publicKey,
                        true /* permanent */
                    );
                } catch (Exception e) {
                    // squash all exceptions
                    // (some tokens cannot store the public key)
                }
            }

            return importedKey;
        } else {
            throw new InvalidKeyException("Unwrapping a private key with"
                + " a private key is not supported");
            /*
            Assert._assert(privKey!=null && pubKey==null && symKey==null);
            return nativeUnwrapPrivWithPriv(token, privKey, wrapped, algorithm,
                        algFromType(type), publicValue, IV, temporary );
            */
        }
    }

    /**
     * Extracts the "public value" from a public key.  The public value is
     *  used to construct the key identifier (CKA_ID). Also, the internal token
     *  stores the EC DSA and EC public value along with the private key.
     */
    private static byte[]
    extractPublicValue(PublicKey publicKey, PrivateKey.Type type)
        throws InvalidKeyException
    {
        /* this code should call a generic function which returns the
         * proper public value. */
        if( publicKey == null ) {
            throw new InvalidKeyException("publicKey is null");
        }
        if( type == PrivateKey.RSA ) {
            if( !(publicKey instanceof RSAPublicKey)) {
                throw new InvalidKeyException("Type of public key does not "+
                    "match type of private key which is RSA");
            }
            return ((RSAPublicKey)publicKey).getModulus().toByteArray();
        } else if(type == PrivateKey.EC) {
            if( !(publicKey instanceof PK11ECPublicKey) ) {
                throw new InvalidKeyException("Type of public key does not "+
                    "match type of private key which is EC");
            }
            return ((PK11ECPublicKey)publicKey).getW().toByteArray();
        } else if(type == PrivateKey.DSA) {
            if( !(publicKey instanceof DSAPublicKey) ) {
                throw new InvalidKeyException("Type of public key does not "+
                    "match type of private key which is DSA");
            }
            return ((DSAPublicKey)publicKey).getY().toByteArray();
        } else {
            throw new InvalidKeyException("Unknown private key type: " + type);
        }
    }


    public SymmetricKey
    unwrapSymmetric(byte[] wrapped, SymmetricKey.Type type,
        SymmetricKey.Usage usage, int keyLen)
        throws TokenException, IllegalStateException,
            InvalidAlgorithmParameterException
    {
        return unwrapSymmetric(wrapped, type, usage.getVal(), keyLen);
    }

    public SymmetricKey
    unwrapSymmetric(byte[] wrapped, SymmetricKey.Type type, int keyLen)
        throws TokenException, IllegalStateException,
            InvalidAlgorithmParameterException
    {
        return unwrapSymmetric(wrapped, type, -1, keyLen);
    }

    public SymmetricKey
    unwrapSymmetricPerm(byte[] wrapped, SymmetricKey.Type type,
        SymmetricKey.Usage usage, int keyLen)
        throws TokenException, IllegalStateException,
            InvalidAlgorithmParameterException
    {
        return unwrapSymmetricPerm(wrapped, type, usage.getVal(), keyLen);
    }

    public SymmetricKey
    unwrapSymmetricPerm(byte[] wrapped, SymmetricKey.Type type, int keyLen)
        throws TokenException, IllegalStateException,
            InvalidAlgorithmParameterException
    {
        return unwrapSymmetricPerm(wrapped, type, -1, keyLen);
    }

    private SymmetricKey
    unwrapSymmetricPerm(byte[] wrapped, SymmetricKey.Type type,
        int usageEnum, int keyLen)
        throws TokenException, IllegalStateException,
            InvalidAlgorithmParameterException
    {
        if( state != UNWRAP ) {
            throw new IllegalStateException();
        }

        /* Since we want permanent,make the temporary arg false */
        boolean temporary = false;


        if( (! algorithm.isPadded()) && (type == SymmetricKey.RC4) ) {
            if( keyLen <= 0 ) {
                throw new InvalidAlgorithmParameterException(
                    "RC4 keys wrapped in unpadded algorithms need key length"+
                    " specified when unwrapping");
            }
        } else {
            // Don't use the key length
            keyLen = 0;
        }

        if( algorithm == KeyWrapAlgorithm.PLAINTEXT ) {
            return nativeUnwrapSymPlaintext(token, wrapped, algFromType(type),
                usageEnum,temporary );
        } else {
            if( symKey != null ) {
                Assert._assert(pubKey==null && privKey==null);
                return nativeUnwrapSymWithSym(token, symKey, wrapped, algorithm,
                        algFromType(type), keyLen, IV, usageEnum,temporary);
            } else {
                Assert._assert(privKey!=null && pubKey==null && symKey==null);
                throw new TokenException("We do not support permnament unwrapping with private key.");
            }
        }
    }


    private SymmetricKey
    unwrapSymmetric(byte[] wrapped, SymmetricKey.Type type,
        int usageEnum, int keyLen)
        throws TokenException, IllegalStateException,
            InvalidAlgorithmParameterException
    {
        if( state != UNWRAP ) {
            throw new IllegalStateException();
        }

        if( (! algorithm.isPadded()) && (type == SymmetricKey.RC4) ) {
            if( keyLen <= 0 ) {
                throw new InvalidAlgorithmParameterException(
                    "RC4 keys wrapped in unpadded algorithms need key length"+
                    " specified when unwrapping");
            }
        } else {
            // Don't use the key length
            //keyLen = 0;
        }

        /* Since we DONT want permanent,make the temporary arg true */
        boolean temporary = true;

        if( algorithm == KeyWrapAlgorithm.PLAINTEXT ) {
            return nativeUnwrapSymPlaintext(token, wrapped, algFromType(type),
                usageEnum, temporary );
        } else {
            if( symKey != null ) {
                Assert._assert(pubKey==null && privKey==null);
                return nativeUnwrapSymWithSym(token, symKey, wrapped, algorithm,
                        algFromType(type), keyLen, IV, usageEnum,temporary);
            } else {
                Assert._assert(privKey!=null && pubKey==null && symKey==null);
                return nativeUnwrapSymWithPriv(token, privKey, wrapped,
                    algorithm, algFromType(type), keyLen, IV, usageEnum );
            }
        }
    }

    private static Algorithm
    algFromType(PrivateKey.Type type) {
        if (type == PrivateKey.RSA) {
            return KeyPairAlgorithm.RSAFamily;
        } else if (type == PrivateKey.DSA) {
            return KeyPairAlgorithm.DSAFamily;
        } else {
            Assert._assert( type == PrivateKey.EC);
            return KeyPairAlgorithm.ECFamily;
	}
    }

    private static Algorithm
    algFromType(SymmetricKey.Type type) {
        if( type == SymmetricKey.DES ) {
            return EncryptionAlgorithm.DES_ECB;
        } else if( type == SymmetricKey.DES3 ) {
            return EncryptionAlgorithm.DES3_ECB;
        } else if( type == SymmetricKey.AES ) {
            return EncryptionAlgorithm.AES_128_ECB;
        }else if( type == SymmetricKey.RC4 ) {
            return EncryptionAlgorithm.RC4;
        } else if( type == SymmetricKey.AES ) {
            return EncryptionAlgorithm.AES_128_ECB;
        } else if( type == SymmetricKey.SHA1_HMAC) {
            return HMACAlgorithm.SHA1;
        } else  {
            Assert._assert( type == SymmetricKey.RC2 );
            return EncryptionAlgorithm.RC2_CBC;
        }
    }

    /**
     * Unwrap a private with a symmetric.
     */
    private static native PrivateKey
    nativeUnwrapPrivWithSym(PK11Token token, SymmetricKey unwrappingKey,
        byte[] wrappedKey, KeyWrapAlgorithm alg, Algorithm type,
        byte[] publicValue, byte[] IV, boolean temporary)
            throws TokenException;

    /**
     * Unwrap a private with a private.
     * NOTE: this is not supported by the security library.
    private static native PrivateKey
    nativeUnwrapPrivWithPriv(PK11Token token, PrivateKey unwrappingKey,
        byte[] wrappedKey, KeyWrapAlgorithm alg, Algorithm type,
        byte[] publicValue, byte[] IV, boolean temporary)
            throws TokenException;
     */

    /**
     * Unwrap a symmetric with a symmetric.
     */
    private static native SymmetricKey
    nativeUnwrapSymWithSym(PK11Token token, SymmetricKey unwrappingKey,
        byte[] wrappedKey, KeyWrapAlgorithm alg, Algorithm type, int keyLen,
        byte[] IV, int usageEnum, boolean temporary)
            throws TokenException;

    /**
     * Unwrap a symmetric with a private.
     */
    private static native SymmetricKey
    nativeUnwrapSymWithPriv(PK11Token token, PrivateKey unwrappingKey,
        byte[] wrappedKey, KeyWrapAlgorithm alg, Algorithm type, int keyLen,
        byte[] IV, int usageEnum)
            throws TokenException;

    private static native SymmetricKey
    nativeUnwrapSymPlaintext(PK11Token token, byte[] wrappedKey,
        Algorithm type, int usageEnum, boolean temporary);

    private void reset() {
        state = UNINITIALIZED;
        symKey = null;
        privKey = null;
        pubKey = null;
        parameters = null;
        IV = null;
    }
}
