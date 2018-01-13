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
import javax.servlet.ServletOutputStream;

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
import com.google.common.hash.Hashing;
import com.google.common.net.MediaType;

import spark.Request;
import spark.Response;
import spark.Spark;
import spark.utils.IOUtils;

public class HostQR
{
  static public class FileQR
  {
    private final String id;
    private final String path;
    
    public FileQR(String id, String path)
    {
      this.id = id;
      this.path = path;
    }
    
    public String getId()
    {
      return id;
    }
    
    public String getPath()
    {
      return path;
    }
  }
  
  private static Object getQR(final Request request, final Response response) throws Exception
  {
    String file = request.url().replace("/qr/", "/files/");

    response.type(MediaType.PNG.toString());
    response.status(200);
    BufferedImage qr = createQR(file, 500);
    ServletOutputStream os = response.raw().getOutputStream();

    ImageIO.write(qr, "png", os);
    os.close();

    return null;
  }

  private static Object getFile(final Request request, final Response response) throws Exception
  {
    File file = new File(request.pathInfo().replaceFirst("/", ""));
    long size = file.length();
    if(size > Integer.MAX_VALUE)
    {
      response.status(404);
      return null;
    }

    InputStream is;
    try
    {
      is = new FileInputStream(file);
    } catch(IOException e)
    {
      response.status(404);
      return null;
    }
    
    response.type(MediaType.OCTET_STREAM.toString());
    response.status(200);
    response.raw().setContentLength((int)size);
    ServletOutputStream os = response.raw().getOutputStream();

    IOUtils.copy(is, os);
    is.close();
    os.close();

    return null;
  }

  private static void addFiles(List<FileQR> qrFiles, File[] filesList,String dir)
  {
    for(File file : filesList)
    {
      String path = dir + file.getName();
      if(file.isFile())
      {
        String id = Hashing.sha256().hashBytes(path.getBytes()).toString();
        qrFiles.add(new FileQR(id,path));
      } else if(file.isDirectory())
      {
        System.out.println(file.getName());
        String sub = dir + file.getName() + "/";
        addFiles(qrFiles,file.listFiles(),sub);
      }
    }
  }

  private static Object listFiles(final Request request, final Response response) throws Exception
  {
    StringWriter writer = new StringWriter();
    VelocityContext ctx = new VelocityContext();
    List<FileQR> qrFiles = new ArrayList<>();
    File dir = new File("files");
    addFiles(qrFiles,dir.listFiles(),"");
    ctx.put("files", qrFiles);
    mainTemplate.merge(ctx, writer);
    return writer.toString();
  }
  

  private static BufferedImage createQR(String myCodeText, int size) throws WriterException
  {
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

    for(int i = 0; i < imgSize; i++)
    {
      for(int j = 0; j < imgSize; j++)
      {
        if(byteMatrix.get(i, j))
        {
          graphics.fillRect(i, j, 1, 1);
        }
      }
    }
    return image;
  }

  static private Template mainTemplate;
  static private VelocityEngine ve;
  
  public static void main(String[] args)
  {
    ve = new VelocityEngine();
    ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath"); 
    ve.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
    ve.init();
    
    mainTemplate = ve.getTemplate("/vm/hostqr.vm");
    
    new File("files").mkdir();
    Spark.get("/", HostQR::listFiles);
    Spark.get("/qr/*", HostQR::getQR);
    Spark.get("/files/*", HostQR::getFile);
  }

}
