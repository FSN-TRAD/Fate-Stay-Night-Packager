package fr.bloomenetwork.fatestaynight.packager;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.unix4j.Unix4j;

public class Utils {

	private static final String[] numbers = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
	private static final int DEFAULT_BUFFER_SIZE = 1024;
	public static final int INFO = 0;
	public static final int DEBUG = 1;
	public static final int ERROR = 2;

	//Retourne l'entier, compris entre 1 et 99, fournit en paramètre
	//en un String écrit en japonais
	public static String numberToJapaneseString(int number) {

		String strNumber = String.format("%02d", number);
		String dix = "十";

		if(strNumber.charAt(0) == '0')
			dix = "";
		if(strNumber.charAt(0) == '1')
			strNumber = "0" + strNumber.charAt(1);

		return numbers[Integer.parseInt(String.valueOf(strNumber.charAt(0)))] + dix + numbers[Integer.parseInt(String.valueOf(strNumber.charAt(1)))];
	}

	public static void writeInputStreamToFile(InputStream inputStream, java.io.File file)
			throws IOException {

		// append = false
		try (FileOutputStream outputStream = new FileOutputStream(file)) {
			int read;
			byte[] bytes = new byte[DEFAULT_BUFFER_SIZE];
			while ((read = inputStream.read(bytes)) != -1) {
				outputStream.write(bytes, 0, read);
			}
		}

	}
	public static void print(String message, int level) {
		String output = "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "]";
		switch(level) {
		case INFO:
			output += "[INFO]";
			break;
		case DEBUG:
			output += "[DEBUG]";
			break;
		case ERROR:
			output += "[ERROR]";
			break;
		}
		output += message;
		System.out.println(output);
	}
	public static void print(String message) {
		print(message, INFO);
	}

	public static void docxToKsFile(InputStream is, String filename) throws IOException {
		ZipInputStream zis = new ZipInputStream(is);
		ByteArrayOutputStream fos = new ByteArrayOutputStream();
		ZipEntry ze = null;
		String xmlContent = null;
		while ((ze = zis.getNextEntry()) != null) {
			if (ze.getName().equals("word/document.xml")) {
				byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
				int len;
				while ((len = zis.read(buffer)) != -1) {
					fos.write(buffer, 0, len);			
				}
				xmlContent =  new String(fos.toString(StandardCharsets.UTF_8));
				fos.close();
				break;
			}
		}
		fos.close();
		String txtContent = xmlContent.replaceAll("</w:p>", "\n"); 
		txtContent = txtContent.replaceAll("<[^>]*/?>", "");
		txtContent = txtContent.replaceAll("&amp;", "&");
		txtContent = txtContent.replaceAll("&quot;", "\"");
		txtContent = txtContent.replaceAll("&lt;", "<");
		txtContent = txtContent.replaceAll("&gt;", ">");
		// Supprime la première ligne vide de tous les fichiers :
		txtContent = txtContent.replaceFirst("\r", "");
		// Remplace l'espace avant la ponctuation par une espace insécable :
		txtContent = txtContent.replaceAll("« ", "«\u00A0");
		txtContent = txtContent.replaceAll(" »", "\u00A0»");
		txtContent = txtContent.replaceAll(" \\?", "\u00A0?");
		txtContent = txtContent.replaceAll(" \\!", "\u00A0!");
		txtContent = txtContent.replaceAll("&\u00A0!", "& \\!"); //Fix
		txtContent = txtContent.replaceAll(" :", "\u00A0:");
		txtContent = txtContent.replaceAll(" ;", "\u00A0;");
		java.nio.file.Files.write(Paths.get(filename), txtContent.getBytes(StandardCharsets.UTF_8));
	}
}
