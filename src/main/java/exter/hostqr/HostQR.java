package exter.hostqr;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.common.net.MediaType;

import spark.Request;
import spark.Response;
import spark.Spark;
import spark.utils.IOUtils;

public class HostQR
{
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
  
  private static Object listFiles(final Request request, final Response response) throws Exception
  {
    StringBuilder output = new StringBuilder();
    output.append("<html><head><style>table,th,td { border: 1px solid black; border-collapse: collapse; } th,td { padding: 15px; padding-bottom: 50%; } </style></head>");
    output.append("<body style=\"background-color:#DFDFDF\"><center><table style=\"width:80%\">\n");
    File dir = new File("files");
    File[] filesList = dir.listFiles();
    for(File file : filesList)
    {
      if(file.isFile())
      {
        output.append("<tr><td><center><h2><p>");
        output.append(file.getName());
        output.append("</p></h2>\n<img src=\"qr/");
        output.append(file.getName());
        output.append("\" /></center></td></tr>\n");
      }
    }
    output.append("</table></center></body></html>");
    return output.toString();
  }

  private static BufferedImage createQR(String myCodeText, int size) throws WriterException
  {
    Map<EncodeHintType, Object> hintMap = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
    hintMap.put(EncodeHintType.CHARACTER_SET, "UTF-8");

    hintMap.put(EncodeHintType.MARGIN, 1);
    hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);

    QRCodeWriter qrCodeWriter = new QRCodeWriter();
    BitMatrix byteMatrix = qrCodeWriter.encode(myCodeText, BarcodeFormat.QR_CODE, size, size, hintMap);
    int CrunchifyWidth = byteMatrix.getWidth();
    BufferedImage image = new BufferedImage(CrunchifyWidth, CrunchifyWidth, BufferedImage.TYPE_INT_RGB);
    image.createGraphics();

    Graphics2D graphics = (Graphics2D) image.getGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, CrunchifyWidth, CrunchifyWidth);
    graphics.setColor(Color.BLACK);

    for(int i = 0; i < CrunchifyWidth; i++)
    {
      for(int j = 0; j < CrunchifyWidth; j++)
      {
        if(byteMatrix.get(i, j))
        {
          graphics.fillRect(i, j, 1, 1);
        }
      }
    }
    return image;
  }
  
  public static void main(String[] args)
  {
    new File("files").mkdir();
    Spark.get("/", (req, res) -> listFiles(req,res));
    Spark.get("/qr/:file", (req, res) -> getQR(req,res));
    Spark.get("/files/*", (req, res) -> getFile(req,res));
  }

}
