package fr.brickcraft.importer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Safe, bounded reader of BrickLink Studio .io files (ZIP and traditional ZipCrypto ZIP).
 * Studio's documented/community-known archive password is a FORMAT constant, not a user secret.
 * Only model.ldr is read: model2.ldr contains expanded geometry and different colour codes.
 * No files are written or extracted to disk.
 */
public final class StudioIoArchive {
    private StudioIoArchive() {}
    public static final int MAX_ARCHIVE_BYTES=96*1024*1024;
    public static final int MAX_MODEL_BYTES=32*1024*1024;
    private static final int LOCAL=0x04034b50, CENTRAL=0x02014b50, END=0x06054b50;
    private static final byte[] PASSWORD="soho0909".getBytes(StandardCharsets.US_ASCII);

    public static String modelText(Path path) throws IOException {
        if(Files.size(path)>MAX_ARCHIVE_BYTES)throw new IOException("Archive .io trop volumineuse (96 Mo max)");
        // Java's ZipFile does not decode traditional password-protected ZIP entries.
        // Try the normal, streaming path first for unencrypted .io archives.
        try(ZipFile zip=new ZipFile(path.toFile())) {
            ZipEntry entry=zip.getEntry("model.ldr");
            if(entry==null)throw new IOException("model.ldr absent de l'archive .io");
            if(entry.getSize()>MAX_MODEL_BYTES)throw new IOException("model.ldr trop volumineux");
            try(InputStream in=zip.getInputStream(entry)) {
                byte[] data=in.readNBytes(MAX_MODEL_BYTES+1);
                if(data.length>MAX_MODEL_BYTES)throw new IOException("model.ldr depasse 32 Mo");
                return new String(data,StandardCharsets.UTF_8);
            }
        }catch(java.util.zip.ZipException encrypted){
            // Some JDKs reject encrypted ZIPs when OPENING the central directory.
            // The fallback verifies both the encrypted header and the uncompressed CRC.
        }
        return new String(decodeZipCryptoModel(path),StandardCharsets.UTF_8);
    }

    private static int u16(byte[] b,int p){return (b[p]&255)|((b[p+1]&255)<<8);}
    private static long u32(byte[] b,int p){return (b[p]&255L)|((b[p+1]&255L)<<8)|((b[p+2]&255L)<<16)|((b[p+3]&255L)<<24);}
    private static boolean inRange(byte[] b,long offset,long size){return offset>=0 && size>=0 && offset<=b.length && size<=b.length-offset;}
    private static int crcUpdate(int key,int value){
        key ^= value&255;
        for(int k=0;k<8;k++)key=(key>>>1)^((key&1)!=0?0xEDB88320:0);
        return key;
    }
    private static byte[] decrypt(byte[] payload){
        int k0=0x12345678,k1=0x23456789,k2=0x34567890;
        for(byte p:PASSWORD){k0=crcUpdate(k0,p);k1=(k1+(k0&255))*134775813+1;k2=crcUpdate(k2,k1>>>24);}
        byte[] out=new byte[payload.length];
        for(int i=0;i<payload.length;i++){
            int v=k2|2;
            int plain=(payload[i]&255)^(((v*(v^1))>>>8)&255);
            out[i]=(byte)plain;
            k0=crcUpdate(k0,plain);k1=(k1+(k0&255))*134775813+1;k2=crcUpdate(k2,k1>>>24);
        }
        return out;
    }
    private static byte[] decodeZipCryptoModel(Path file) throws IOException {
        byte[] zip=Files.readAllBytes(file);
        if(zip.length<22)throw new IOException("Archive .io tronquee");
        int eocd=-1;
        for(int p=zip.length-22;p>=Math.max(0,zip.length-22-65535);p--){
            if(u32(zip,p)==END && inRange(zip,p,22) && p+22+u16(zip,p+20)==zip.length){eocd=p;break;}
        }
        if(eocd<0)throw new IOException("Annuaire ZIP absent ou ZIP64 non supporte");
        if(u16(zip,eocd+4)!=0 || u16(zip,eocd+6)!=0 || u16(zip,eocd+8)!=u16(zip,eocd+10))
            throw new IOException("ZIP multi-volume non supporte");
        long count=u16(zip,eocd+10),offset=u32(zip,eocd+16),size=u32(zip,eocd+12);
        if(count>20_000||!inRange(zip,offset,size)||offset+size>eocd)throw new IOException("Annuaire ZIP invalide");
        long cursor=offset;
        for(int i=0;i<count;i++){
            if(!inRange(zip,cursor,46) || u32(zip,(int)cursor)!=CENTRAL)throw new IOException("Entree ZIP corrompue");
            int p=(int)cursor;
            int flags=u16(zip,p+8),method=u16(zip,p+10),checkTime=u16(zip,p+12);
            long crc=u32(zip,p+16),compressed=u32(zip,p+20),expanded=u32(zip,p+24),local=u32(zip,p+42);
            int nameLength=u16(zip,p+28),extra=u16(zip,p+30),comment=u16(zip,p+32);
            long len=46L+nameLength+extra+comment;
            if(!inRange(zip,cursor,len)||cursor+len>offset+size)throw new IOException("Metadonnees ZIP invalides");
            String name=new String(zip,p+46,nameLength,StandardCharsets.UTF_8);
            cursor+=len;
            if(!name.equals("model.ldr"))continue;
            if((flags&1)==0)throw new IOException("Entree non chiffree mais inaccessible (ZIP endommage)");
            if((flags&64)!=0 || (flags&32)!=0)throw new IOException("Chiffrement ZIP avance non supporte");
            if(method!=8 && method!=0)throw new IOException("Compression du modele non supportee");
            if(expanded>MAX_MODEL_BYTES||compressed>MAX_ARCHIVE_BYTES||compressed<12)throw new IOException("Entree du modele trop volumineuse");
            if(!inRange(zip,local,30)||u32(zip,(int)local)!=LOCAL)throw new IOException("En-tete ZIP local invalide");
            int n=u16(zip,(int)local+26),e=u16(zip,(int)local+28);
            long start=local+30L+n+e;
            if(!inRange(zip,start,compressed))throw new IOException("Contenu ZIP tronque");
            byte[] raw=new byte[(int)compressed];
            System.arraycopy(zip,(int)start,raw,0,raw.length);
            byte[] clear=decrypt(raw);
            int expected=(flags&8)!=0?checkTime>>>8:(int)(crc>>>24);
            if((clear[11]&255)!=expected)throw new IOException("En-tete ZIP Studio chiffre invalide");
            byte[] result;
            if(method==0){
                if(clear.length-12>MAX_MODEL_BYTES)throw new IOException("Modele trop volumineux");
                result=java.util.Arrays.copyOfRange(clear,12,clear.length);
            }else{
                Inflater inflater=new Inflater(true);
                try(InputStream input=new InflaterInputStream(
                        new ByteArrayInputStream(clear,12,clear.length-12),inflater);
                    ByteArrayOutputStream output=new ByteArrayOutputStream(Math.min(65536,(int)expanded))){
                    byte[] buf=new byte[8192];int got;
                    while((got=input.read(buf))!=-1){
                        if(output.size()+got>MAX_MODEL_BYTES)throw new IOException("Expansion ZIP trop volumineuse");
                        output.write(buf,0,got);
                    }
                    result=output.toByteArray();
                }finally{inflater.end();}
            }
            CRC32 checksum=new CRC32();checksum.update(result);
            if(result.length!=expanded || checksum.getValue()!=crc)throw new IOException("Integrite du modele ZIP invalide (CRC)");
            return result;
        }
        throw new IOException("model.ldr absent de l'annuaire ZIP");
    }
}
