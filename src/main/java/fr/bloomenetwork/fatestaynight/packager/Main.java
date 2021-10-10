package fr.bloomenetwork.fatestaynight.packager;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Color;
import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import java.awt.event.*;
import java.io.File;

public class Main extends JFrame {
    
    //Paramètres
    private String outputFolder = "package";
    
    //Composants graphiques
    private JButton connectionButton;
    private JTextField outputFolderTextField;
    private JTextArea textOutput;
    private JProgressBar progressBarFate;
    private JProgressBar progressBarUBW;
    private JProgressBar progressBarHF;

    //Gestion de l'API Google Drive
    private GoogleAPI googleAPI = null;
    
    public Main() {
        
        //Configuration des divers éléments graphiques
        connectionButton = new JButton("Télécharger");
        outputFolderTextField = new JTextField(outputFolder);
        //ajouter le listener sur le JTextField
        outputFolderTextField.addActionListener(new ActionListener() {
            //capturer un événement sur le JTextField
            public void actionPerformed(ActionEvent e) {
                //récupérer et afficher le contenu de JTextField dans la console
                Utils.print("Nouveau répertoire de destination : " + outputFolderTextField.getText());

                //Crée un nouveau répertoire s'il n'existe pas déjà
                createDirectory();
            }
        });
        textOutput = new JTextArea();
        textOutput.setRows(15);
        textOutput.setEditable(false);
        System.setOut(new PrintStreamCapturer(textOutput, System.out));
        System.setErr(new PrintStreamCapturer(textOutput, System.err, "[ERROR]"));
        JPanel topPane = new JPanel();
        JPanel progressPane = new JPanel();
        progressBarFate = new JProgressBar();
        progressBarFate.setMinimum(0);
        progressBarFate.setStringPainted(true);
        progressBarFate.setForeground(new Color(5, 67, 149));
        progressBarFate.setBorderPainted(false);
        progressBarUBW = new JProgressBar();
        progressBarUBW.setMinimum(0);
        progressBarUBW.setStringPainted(true);
        progressBarUBW.setForeground(new Color(134, 18, 5));
        progressBarUBW.setBorderPainted(false);
        progressBarHF = new JProgressBar();
        progressBarHF.setMinimum(0);
        progressBarHF.setStringPainted(true);
        progressBarHF.setForeground(new Color(98, 46, 98));
        progressBarHF.setBorderPainted(false);
        
        //Listener sur le premier bouton qui permet d'initialiser le service de l'API Google
        connectionButton.addActionListener(e -> {
            try {
                googleAPI = new GoogleAPI();
                connectionButton.setEnabled(false);
                connectionButton.setText("Connecté");
                Utils.print("Connecté à l'API Google Drive.\n");
            } catch (GeneralSecurityException | IOException e1) {
                Utils.print(e1.toString(), Utils.ERROR);
            }

            // Démarre le téléchargement de tous les fichiers de script dans différents Thread
            FetchingThread ftFate = new FetchingThread(googleAPI, progressBarFate, "Fate");
            FetchingThread ftUBW = new FetchingThread(googleAPI, progressBarUBW, "Unlimited Blade Works");
            FetchingThread ftHF = new FetchingThread(googleAPI, progressBarHF, "Heavens Feel");
            FetchingThread ftStatuts = new FetchingThread(googleAPI, progressBarHF, "Statuts");

            ftFate.setOutputFolder(outputFolderTextField.getText());
            ftUBW.setOutputFolder(outputFolderTextField.getText());
            ftHF.setOutputFolder(outputFolderTextField.getText());
            ftStatuts.setOutputFolder(outputFolderTextField.getText());
            outputFolderTextField.setEditable(false);
            Thread tFate = new Thread(ftFate);
            tFate.start();
            Thread tUBW = new Thread(ftUBW);
            tUBW.start();
            Thread tHF = new Thread(ftHF);
            tHF.start();
            Thread tStatuts = new Thread(ftStatuts);
            tStatuts.start();
            // Crée le répertoire si celui-ci n'existe pas
            createDirectory();
        });
        
        //Mise en page de la fenêtre 
        this.setTitle("Fate/Stay Night Packager - 0.7 requinDr");
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setSize(600, 374);
        topPane.add(new JLabel(" API Google + début : "));
        topPane.add(connectionButton);
        topPane.add(new JLabel(" Répertoire de sortie : "));
        topPane.add(outputFolderTextField);
        topPane.setLayout(new GridLayout(2, 2));    
        
        progressPane.add(new JLabel(" Fate ", JLabel.CENTER));
        progressPane.add(new JLabel(" Unlimited Blade Works ", JLabel.CENTER));
        progressPane.add(new JLabel(" Heaven's Feel + Statuts ", JLabel.CENTER));

        progressPane.add(progressBarFate, BorderLayout.CENTER);
        progressPane.add(progressBarUBW, BorderLayout.CENTER);
        progressPane.add(progressBarHF, BorderLayout.CENTER);

        GridLayout gridLayout = new GridLayout(2,3);
        progressPane.setLayout(gridLayout);
        

        this.add(topPane, BorderLayout.NORTH);
        this.add(progressPane, BorderLayout.CENTER);
        JScrollPane scrollPane = new JScrollPane(textOutput);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        this.add(scrollPane, BorderLayout.SOUTH);
        
        this.setLocationRelativeTo(null);
        this.setVisible(true);
    }

    /**
     * Crée un répertoire du nom du JTextField si celui-ci n'existe pas déjà
     */
    public void createDirectory() {
        String directoryName = outputFolderTextField.getText();

        File directory = new File(directoryName);
        if (!directory.exists()){
            Utils.print("Répertoire \" " + directoryName + " \" inexistant donc créé\n");
            directory.mkdir();
        }
    }

    public static void main(String[] args) {
        Main main = new Main();
    }

}
