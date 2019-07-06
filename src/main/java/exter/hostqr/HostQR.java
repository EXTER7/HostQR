package exter.hostqr;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import io.javalin.Javalin;
import io.javalin.http.Context;

import com.google.common.hash.Hashing;
import com.google.common.net.MediaType;


public class HostQR
{
    static public class FileQR
    {
        private final String id;
        private final String path;
        
        public FileQR(String id, String path) {
            this.id = id;
            this.path = path;
        }
        
        public String getId() {
            return id;
        }
        
        public String getPath() {
            return path;
        }
    }
      
    private static void getQR(Context ctx) throws Exception {
        String file = ctx.url().replace("/qr/", "/files/");

        ctx.contentType(MediaType.PNG.toString());
        ctx.status(200);
        BufferedImage qr = createQR(file, 500);
        ImageIO.write(qr, "png", ctx.res.getOutputStream());
    }
    
    private static void getFile(Context ctx) throws Exception {
        File file = new File(ctx.path().replaceFirst("/", ""));
        long size = file.length();
        if(size > Integer.MAX_VALUE) {
            ctx.status(404);
        }
    
        try(InputStream is = new FileInputStream(file)) {
            ctx.contentType(MediaType.OCTET_STREAM.toString());
            ctx.status(200);
            ctx.res.setContentLengthLong(size);
            IOUtils.copy(is,ctx.res.getOutputStream());
        } catch(IOException e) {
            ctx.status(404);
        }
    }
    
    private static void addFiles(List<FileQR> qrFiles, File[] filesList,String dir) {
        for(File file : filesList) {
            String path = dir + file.getName();
            if(file.isFile()) {
                String id = Hashing.sha256().hashBytes(path.getBytes()).toString();
                qrFiles.add(new FileQR(id,path));
            } else if(file.isDirectory()) {
                String sub = dir + file.getName() + "/";
                addFiles(qrFiles,file.listFiles(),sub);
            }
        }
    }
    
    private static void listFiles(Context ctx) throws Exception {
        StringWriter writer = new StringWriter();
        VelocityContext vctx = new VelocityContext();
        List<FileQR> qrFiles = new ArrayList<>();
        File dir = new File("files");
        addFiles(qrFiles,dir.listFiles(),"");
        vctx.put("files", qrFiles);
        mainTemplate.merge(vctx, writer);
        ctx.html(writer.toString());
    }
      
    
    private static BufferedImage createQR(String myCodeText, int size) throws WriterException {
        Map<EncodeHintType, Object> hintMap = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
        hintMap.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hintMap.put(EncodeHintType.MARGIN, 1);
        hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
    
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix byteMatrix = qrCodeWriter.encode(myCodeText, BarcodeFormat.QR_CODE, size, size, hintMap);
        int imgSize = byteMatrix.getWidth();
        BufferedImage image = new BufferedImage(imgSize, imgSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
    
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, imgSize, imgSize);
        graphics.setColor(Color.BLACK);
    
        for(int i = 0; i < imgSize; i++) {
            for(int j = 0; j < imgSize; j++) {
                if(byteMatrix.get(i, j)) {
                  graphics.fillRect(i, j, 1, 1);
                }
            }
        }
        return image;
    }
    
    static private Template mainTemplate;
    static private VelocityEngine ve;
      
    public static void main(String[] args) {
        ve = new VelocityEngine();
        ve.setProperty(RuntimeConstants.RESOURCE_LOADERS,RuntimeConstants.RESOURCE_LOADER_CLASS);
        ve.setProperty(RuntimeConstants.RESOURCE_LOADER + "." + RuntimeConstants.RESOURCE_LOADER_CLASS + "." + RuntimeConstants.RESOURCE_LOADER_CLASS,
                       ClasspathResourceLoader.class.getName()); 
        ve.init();
    
        mainTemplate = ve.getTemplate("/vm/hostqr.vm");
        
        new File("files").mkdir();
        Javalin web = Javalin.create(config -> {
            config.autogenerateEtags = true;
            config.asyncRequestTimeout = 10000L;
            config.logIfServerNotStarted = true;
            config.dynamicGzip = true;
            config.enforceSsl = false;
        }).start(8080);    
        web.get("/", HostQR::listFiles);
        web.get("/qr/*", HostQR::getQR);
        web.get("/files/*", HostQR::getFile);
    }
}
