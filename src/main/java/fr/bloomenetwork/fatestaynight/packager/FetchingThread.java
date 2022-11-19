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
	private static Pattern fcfPattern = Pattern.compile(".+\\.fcf");
	private static Pattern dicPattern = Pattern.compile(".+\\.dic");
	private static Pattern poPattern = Pattern.compile("msgid \"");
	private static Pattern scenarioPattern = Pattern.compile("@resetvoice ");
	private static Pattern routePattern = Pattern.compile("@resetvoice route=(\\w+) day=(\\d+) scene=(\\d+)");
	private static Pattern prologuePattern = Pattern.compile("@resetvoice route=prologue day=(\\d+)");
	private static Pattern epiloguePattern = Pattern.compile("@resetvoice route=(\\w+)ep(\\d?)");

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
		}

		if (rootFolder != null) {
			//On récupère les sous-dossiers, qui correspondent aux différentes routes
			List<File> routeFolders = null;
			try {
				routeFolders = googleAPI.getSubFiles(rootFolder, " and mimeType = 'application/vnd.google-apps.folder' and name = '" + folderToDownload + "'");
				//routeFolders = googleAPI.getSubFiles(rootFolder, " and mimeType = 'application/vnd.google-apps.folder'");
			} catch (IOException e1) {
				Utils.print(e1.toString(), Utils.ERROR);
			}

			//On récupère ensuite tous les Google Docs qui se trouvent dans les sous-dossiers,
			//ceux correspondants aux jours, des dossiers des routes.
			ArrayList<File> listGdocs = new ArrayList<>();

			for(File routeFolder : routeFolders) {
				try {
					List<File> dayFolders = googleAPI.getSubFiles(routeFolder.getId(), " and mimeType = 'application/vnd.google-apps.folder'");
					listGdocs.addAll(googleAPI.getSubFiles(routeFolder.getId(), " and mimeType = 'application/vnd.google-apps.document'"));
					for(File dayFolder : dayFolders) {
						listGdocs.addAll(googleAPI.getSubFiles(dayFolder.getId(), " and mimeType = 'application/vnd.google-apps.document'"));
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
					/*
					content = googleAPI.getGdoc(file.getId());
					/*/
					InputStream docxStream = googleAPI.getDocx(file.getId());
					content = Utils.docxToTxt(docxStream);
					//*/
					if ((matcher = scenarioPattern.matcher(content)).find()) {
						int index = matcher.start();
						if ((matcher = routePattern.matcher(content.substring(index))).find()) {
							String route, day, scene;
							route = matcher.group(1);
							day   = matcher.group(2);
							scene = matcher.group(3);
	
							boolean h = file.getName().contains("(H)");
	
							//Génération du nom du fichier
							filename = String.format("%s%sルート%s日目-%02d.ks",
									h ? "h/" : "",
									routes.get(route),
									Utils.numberToJapaneseString(Integer.parseInt(day)),
									(h ? 100 : 0) + Integer.parseInt(scene));
						}
						// fichier de scnério du prologue
						else if ((matcher = prologuePattern.matcher(content)).find()){
							String day;
							day = matcher.group(1);

							filename = String.format("プロローグ%s日目.ks", day);
						}
						// fichier de scnério de l'épilogue
						else if ((matcher = epiloguePattern.matcher(content)).find()){
							String route, ep;
							route = matcher.group(1);
							ep = matcher.group(2);

							filename = String.format("%sエピローグ%s.ks", routes.get(route), ep);
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
		} else {
			Utils.print("Le répertoire de base n'a pas été trouvé.", Utils.ERROR);
		}
	}

}
