package fr.bloomenetwork.fatestaynight.packager;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Utils {

	//private static final byte[] UTF8_BOM = {(byte)0xEF, (byte)0xBB, (byte)0xBF};
	private static final String[] NUMBERS_JAP = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};
	private static final int DEFAULT_BUFFER_SIZE = 1024;
	public static final int INFO = 0;
	public static final int DEBUG = 1;
	public static final int ERROR = 2;
	public static final int SYNTAX = 3;

	private static boolean[] level_printed = new boolean[]{true, true, true, false};
	private static boolean[] level_logged = new boolean[]{false, false, true, true};
	private static StringBuilder log = new StringBuilder();

	//Retourne l'entier, compris entre 1 et 99, fournit en paramètre
	//en un String écrit en japonais
	public static String numberToJapaneseString(int number) {
		int tens  = (number / 10) % 10,
		    units = number % 10;
        switch(tens) {
            case 0 :
                return NUMBERS_JAP[units];
            case 1 :
                return NUMBERS_JAP[10] + NUMBERS_JAP[units];
            default :
                return NUMBERS_JAP[tens] + NUMBERS_JAP[10] + NUMBERS_JAP[units];
        }
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
		if(level_logged[level] || level_printed[level]) {
			String output;
			switch(level) {
			case INFO:
				output = "[INFO]";
				break;
			case DEBUG:
				output = "[DEBUG]";
				break;
			case ERROR:
				output = "[ERROR]";
				break;
			case SYNTAX:
				output = "[SYNTAX]";
				break;
			default:
				output = "";
			}
			output += message;
			if(level_printed[level]) {
				String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
				System.out.println("[" + date + "]" + output);
			}
			if (level_logged[level])
				log.append(output).append("\n");
		}
	}
	public static void print(String message) {
		print(message, INFO);
	}

	public static void setLevelPrinted(int level, boolean enabled) {
		level_printed[level] = enabled;
	}

	public static void setLevelLogged(int level, boolean enabled) {
		level_logged[level] = enabled;
	}
	public static boolean isLevelPrinted(int level) {
		return level_printed[level];
	}
	public static boolean isLevelLogged(int level) {
		return level_logged[level];
	}

	public static void saveLog(String filename) {
		try {
			java.nio.file.Files.write(Paths.get(filename), log.toString().getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void processKs(String fileName, String docName, String content) throws IOException {
		try {
			if (fileName.endsWith(".ks"))
				content = TextProcess.fixScenarioFile(docName, content);
			else if (fileName.endsWith(".po"))
				content = TextProcess.fixTranslationFile(docName, content);
		} catch (Exception e) {
			e.printStackTrace();
		}
		java.nio.file.Path path = Paths.get(fileName);
		java.nio.file.Files.createDirectories(path.getParent());
		java.nio.file.Files.writeString(path, content, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.WRITE,
				StandardOpenOption.TRUNCATE_EXISTING);
	}
	

    /**
     * Replaces all docx tags with the appropriate text equivalent.
     * Removes the first \r at the top of the file
     * @param content - the input string to convert
     * @return the converted string
     */
    public static String docxToTxt(String content) {
		return content.replaceAll("</w:p>", "\n")
                      .replaceAll("<w:br[^>]*>", "\n")
                      .replaceAll("<[^>]*/?>", "")
                      .replaceAll("&amp;", "&")
                      .replaceAll("&quot;", "\"")
                      .replaceAll("&lt;", "<")
                      .replaceAll("&gt;", ">")
                      .replaceFirst("\r", "");
    }

	public static String docxToTxt(InputStream is) throws IOException {
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

		return docxToTxt(xmlContent);
	}
}
