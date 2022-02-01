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

public class Utils {

	private static final String[] numbers = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
	private static final int DEFAULT_BUFFER_SIZE = 1024;
	public static final int INFO = 0;
	public static final int DEBUG = 1;
	public static final int ERROR = 2;
	public static final int SYNTAX = 3;

	private static boolean[] level_printed = new boolean[]{true, true, true, true};
	private static boolean[] level_logged = new boolean[]{false, false, false, false};
	private static StringBuilder log = new StringBuilder();

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
		if(level_logged[level] || level_printed[level]) {
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
			case SYNTAX:
				output += "[SYNTAX]";
				break;
			}
			output += message;
			if(level_printed[level])
				System.out.println(output);
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

	public static void docxToKsFile(InputStream is, String filename, String docName) throws IOException {
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

		String txtContent = TextProcess.docxToTxt(xmlContent);
		try {
			if (filename.endsWith(".ks"))
				txtContent = TextProcess.fixSyntax(docName, txtContent);
		} catch (Exception e) {
			e.printStackTrace();
		}

		java.nio.file.Files.write(Paths.get(filename), txtContent.getBytes(StandardCharsets.UTF_8));
	}
}
