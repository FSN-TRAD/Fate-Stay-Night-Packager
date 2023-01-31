package fr.bloomenetwork.fatestaynight.packager;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JProgressBar;

import com.google.api.services.drive.model.File;

public class FetchingThread implements Runnable {

	private GoogleAPI googleAPI;
	private static final String FILTER_GDRIVE_FOLDER = " and mimeType = 'application/vnd.google-apps.folder'";
	private static final String FILTER_GDRIVE_DOCUMENT = " and mimeType = 'application/vnd.google-apps.document'";
	private static final String FILTER_GDRIVE_NAME = " and name = '%s'";
	private static Pattern fcfPattern = Pattern.compile(".+\\.fcf");
	private static Pattern dicPattern = Pattern.compile(".+\\.dic");
	private static Pattern poPattern = Pattern.compile("msgid \"");
	private static Pattern routePattern = Pattern.compile("@resetvoice route=(\\w+)( day=(\\d+))?( scene=(\\d+))?");
	private static Pattern epiloguePattern = Pattern.compile("(\\w+)ep(\\d?)");
	private static Pattern demoPattern = Pattern.compile("D[eé]mo\\s?-\\s?(\\d+)");

    //Listes du nom des routes pour le nom des fichiers
	private static final Map<String, String> routes = new HashMap<String, String>() {
		{
			put("saber" , "セイバー");
			put("rin"   , "凛");
			put("sakura", "桜");
			put(null    , ""); // juste au cas où
		}
	};

    //Composants graphiques
    private String outputFolder;
    private JProgressBar progressBar;
	private String folderToDownload;

	public FetchingThread(GoogleAPI googleAPI, JProgressBar progressBar, String folderToDownload) {
		this.googleAPI = googleAPI;
		this.outputFolder = "package";
		this.progressBar = progressBar;
		this.folderToDownload = folderToDownload;
	}

	//Permet de définir le répertoire de sortie
	public void setOutputFolder(String outputFolder) {
		this.outputFolder = outputFolder;
	}

	//Implémentation de l'interface Runnable
	//Thread qui télécharge les scripts
	public void run() {

		//Récupération du dossier racine grâce à son nom
		String rootFolder = null;
		try {
			rootFolder = googleAPI.getFolderIdByName("Fate Stay Night");
		} catch (Exception e1) {
			Utils.print(e1.toString(), Utils.ERROR);
			return;
		}
		if (rootFolder == null) {
			Utils.print("Le répertoire de base n'a pas été trouvé.", Utils.ERROR);
			return;
		}

		//On récupère les sous-dossiers, qui correspondent aux différentes routes
		List<File> routeFolders = null;
		try {
			routeFolders = googleAPI.getSubFiles(rootFolder, FILTER_GDRIVE_FOLDER + String.format(FILTER_GDRIVE_NAME, folderToDownload));
		} catch (IOException e1) {
			Utils.print(e1.toString(), Utils.ERROR);
			return;
		}

		//On récupère ensuite tous les Google Docs qui se trouvent dans les sous-dossiers,
		//ceux correspondants aux jours, des dossiers des routes.
		ArrayList<File> listGdocs = new ArrayList<>();

		for(File routeFolder : routeFolders) {
			try {
				List<File> dayFolders = googleAPI.getSubFiles(routeFolder.getId(), FILTER_GDRIVE_FOLDER);
				listGdocs.addAll(googleAPI.getSubFiles(routeFolder.getId(), FILTER_GDRIVE_DOCUMENT));
				for(File dayFolder : dayFolders) {
					listGdocs.addAll(googleAPI.getSubFiles(dayFolder.getId(), FILTER_GDRIVE_DOCUMENT));
				}
			} catch (IOException e1) {
				Utils.print(e1.toString(), Utils.ERROR);
			}
		}
		listGdocs.sort(new Comparator<File>() {
			@Override
			public int compare(File o1, File o2) {
				return o1.getName().compareTo(o2.getName());
			}
		});

		progressBar.setMaximum(listGdocs.size());
		Utils.print(folderToDownload + " : " + listGdocs.size() + " fichiers à télécharger.");

		int i = 0;

		//Boucle qui télécharge chaque Google Doc
		Matcher matcher;
		for(File file : listGdocs) {
			//Màj de la progress bar
			i++;
			progressBar.setValue(i);

			String content, filename = "";

			//On vérifie si c'est un fichier .fcf
			matcher = fcfPattern.matcher(file.getName());
			if (matcher.find()){
				Utils.print("fichier FCF ignoré : " + file.getName());
				continue;
			}
			//On vérifie si c'est un fichier .dic
			matcher = dicPattern.matcher(file.getName());
			if (matcher.find()){
				Utils.print("fichier DIC ignoré : " + file.getName());
				continue;
			}
			try {
				InputStream docxStream = googleAPI.getDocx(file.getId());
				content = Utils.docxToTxt(docxStream);
				if (content.contains("*page0|")) {
					// fichiers de scénario de la démo
					if (file.getName().startsWith("Demo")) {
						if ((matcher = demoPattern.matcher(file.getName())).find(0)) {
							String demo_num = matcher.group(1);
							switch(demo_num)
							{
							case "1" : case "2" : case "3" :
								filename = String.format("体験版プロローグ%s日目.ks", demo_num);
								break;
							case "Video" :
								filename = "体験版ダイジェスト仮組04.ks";
								break;
							default :
								Utils.print("Fichier " + file.getName() + " non supporté.", Utils.ERROR);
							}
														
						}
					}
					//autres fichiers scenario
					else if ((matcher = routePattern.matcher(content)).find()) {
						String route, day, scene;
						route = matcher.group(1);
						day   = matcher.group(3);
						scene = matcher.group(5);

						matcher = epiloguePattern.matcher(route);
						boolean epilogue = matcher.find();
						if (epilogue) {
							route = matcher.group(1);
							scene = matcher.group(2);
						}
						boolean h = file.getName().contains("(H)");

						if (epilogue) {
							filename = String.format("%sエピローグ%s.ks", routes.get(route), scene);
						} else {
							switch(route) {
							case "saber" : case "rin" : case "sakura" :
								String japDay = Utils.numberToJapaneseString(Integer.parseInt(day));
								String japRoute = routes.get(route);
								filename = String.format("%s%sルート%s日目-%02d.ks", 
									h ? "h/" : "", japRoute, japDay,
									(h ? 100 : 0) + Integer.parseInt(scene));
								break;
							case "prologue" :
								filename = String.format("プロローグ%s日目.ks", day);
								break;
							case "tigersp" :
								filename = "タイガー道場すぺしゃる.ks";
								break;
							default :
								Utils.print("Fichier scénario " + file.getName() + " non supporté.", Utils.ERROR);
								filename = "";
							}
						}
					}
					else {
						Utils.print("Fichier scénario " + file.getName() + " non supporté.", Utils.ERROR);
					}
				} else if ((matcher = poPattern.matcher(content)).find()) {
					filename = file.getName() + ".po";
				}
				else {
					Utils.print("Fichier " + file.getName() + " non supporté.", Utils.ERROR);
				}

				if (!filename.equals("")) {
					//On écrit le docx
					//Utils.print("\tTéléchargement du fichier docx et conversion.");
					Utils.processKs(outputFolder + "/" + filename, file.getName(), content);
					Utils.print("Fichier " + filename +" écrit  \t(" + file.getName() + ").");
				}
			} catch (IOException e1) {
				Utils.print("Erreur lors de l'écriture de " + filename + "(" + file.getName() + ").", Utils.ERROR);
			} catch (Exception e1) {
				Utils.print("Fichier " + file.getName() + " invalide.", Utils.ERROR);
			}
		}
		Utils.print(folderToDownload + " entièrement téléchargé !");
	}

}
