package org.mishnatyosef.pickup;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Base64;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Calendar;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Token vault with per-source ciphertext and Android-version-specific key protection. */
final class SecretStore {
    private static final String PREFS="mishnat_secret_store";
    private static final String AES_ALIAS="mishnat_source_aes_v1";
    private static final String RSA_ALIAS="mishnat_source_rsa_v1";
    private final Context context;
    private final SharedPreferences prefs;
    SecretStore(Context c){context=c.getApplicationContext();prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}

    synchronized void put(String sourceId,String secret)throws Exception {
        byte[] plain=secret.getBytes("UTF-8");String value;
        if(Build.VERSION.SDK_INT>=23) {
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,aesKey23());
            value="A:"+b64(c.getIV())+":"+b64(c.doFinal(plain));
        } else if(Build.VERSION.SDK_INT>=18) {
            byte[] raw=new byte[16];new SecureRandom().nextBytes(raw);
            SecretKeySpec key=new SecretKeySpec(raw,"AES");
            Cipher data=Cipher.getInstance("AES/CBC/PKCS5Padding");data.init(Cipher.ENCRYPT_MODE,key);
            Cipher wrap=Cipher.getInstance("RSA/ECB/PKCS1Padding");wrap.init(Cipher.WRAP_MODE,rsaPair().getPublic());
            value="R:"+b64(wrap.wrap(key))+":"+b64(data.getIV())+":"+b64(data.doFinal(plain));
        } else {
            byte[] mask=new byte[plain.length];new SecureRandom().nextBytes(mask);
            for(int i=0;i<plain.length;i++)plain[i]=(byte)(plain[i]^mask[i]);
            value="P:"+b64(mask)+":"+b64(plain);
        }
        if(!prefs.edit().putString("token_"+sourceId,value).commit())throw new Exception("לא ניתן לשמור את הטוקן.");
    }

    synchronized String get(String sourceId) {
        String value=prefs.getString("token_"+sourceId,"");if(value.isEmpty())return null;
        try {
            String[] p=value.split(":");
            if("A".equals(p[0])&&Build.VERSION.SDK_INT>=23) {
                Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.DECRYPT_MODE,aesKey23(),new javax.crypto.spec.GCMParameterSpec(128,Base64.decode(p[1],Base64.NO_WRAP)));
                return new String(c.doFinal(Base64.decode(p[2],Base64.NO_WRAP)),"UTF-8");
            }
            if("R".equals(p[0])&&Build.VERSION.SDK_INT>=18) {
                Cipher unwrap=Cipher.getInstance("RSA/ECB/PKCS1Padding");unwrap.init(Cipher.UNWRAP_MODE,rsaPair().getPrivate());
                SecretKey key=(SecretKey)unwrap.unwrap(Base64.decode(p[1],Base64.NO_WRAP),"AES",Cipher.SECRET_KEY);
                Cipher c=Cipher.getInstance("AES/CBC/PKCS5Padding");
                c.init(Cipher.DECRYPT_MODE,key,new IvParameterSpec(Base64.decode(p[2],Base64.NO_WRAP)));
                return new String(c.doFinal(Base64.decode(p[3],Base64.NO_WRAP)),"UTF-8");
            }
            if("P".equals(p[0])) {
                byte[] mask=Base64.decode(p[1],Base64.NO_WRAP),data=Base64.decode(p[2],Base64.NO_WRAP);
                for(int i=0;i<data.length;i++)data[i]=(byte)(data[i]^mask[i]);
                return new String(data,"UTF-8");
            }
        }catch(Exception ignored){}
        return null;
    }
    synchronized void delete(String id){prefs.edit().remove("token_"+id).commit();}
    private String b64(byte[] b){return Base64.encodeToString(b,Base64.NO_WRAP);}

    @TargetApi(23)
    private SecretKey aesKey23()throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        java.security.Key existing=ks.getKey(AES_ALIAS,null);if(existing instanceof SecretKey)return (SecretKey)existing;
        KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");
        android.security.keystore.KeyGenParameterSpec spec=new android.security.keystore.KeyGenParameterSpec.Builder(
                AES_ALIAS,android.security.keystore.KeyProperties.PURPOSE_ENCRYPT|android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes("GCM").setEncryptionPaddings("NoPadding").setRandomizedEncryptionRequired(true).build();
        g.init(spec);return g.generateKey();
    }
    @TargetApi(18)
    private KeyPair rsaPair()throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias(RSA_ALIAS))return new KeyPair(ks.getCertificate(RSA_ALIAS).getPublicKey(),
                (java.security.PrivateKey)ks.getKey(RSA_ALIAS,null));
        Calendar start=Calendar.getInstance(),end=Calendar.getInstance();end.add(Calendar.YEAR,25);
        android.security.KeyPairGeneratorSpec spec=new android.security.KeyPairGeneratorSpec.Builder(context)
                .setAlias(RSA_ALIAS).setSubject(new javax.security.auth.x500.X500Principal("CN=Mishnat Pickup"))
                .setSerialNumber(BigInteger.ONE).setStartDate(start.getTime()).setEndDate(end.getTime())
                .setKeySize(2048).build();
        KeyPairGenerator g=KeyPairGenerator.getInstance("RSA","AndroidKeyStore");g.initialize(spec);return g.generateKeyPair();
    }
}
