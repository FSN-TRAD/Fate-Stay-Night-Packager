package fr.bloomenetwork.fatestaynight.packager;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.BorderFactory;

import java.awt.event.*;
import java.io.File;

public class Main extends JFrame {

    //Paramètres
    private String outputFolder = "package";

    //Composants graphiques
    private JButton connectionButton;
    private JTextField tfOutputFolder;
    private JTextField tfLogFile;
    private JButton saveLogButton;
    private JTextArea textOutput;
    private JProgressBar progressBarFate;
    private JProgressBar progressBarUBW;
    private JProgressBar progressBarHF;

    //Gestion de l'API Google Drive
    private GoogleAPI googleAPI = null;

    public Main() {
        JPanel configPane = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.NONE;
        constraints.insets = new Insets(3,3,3,3);
        constraints.anchor = GridBagConstraints.FIRST_LINE_START;
        //Configuration des divers éléments graphiques
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 3;
        configPane.add(new JLabel("Répertoire de sortie :"), constraints);
        tfOutputFolder = new JTextField(outputFolder, 16);
        constraints.gridx = 3;
        constraints.gridwidth = 3;
        configPane.add(tfOutputFolder, constraints);
        connectionButton = new JButton("Télécharger");
        constraints.gridx = 6;
        constraints.gridwidth = 1;
        configPane.add(connectionButton, constraints);
        //ajouter le listener sur le JTextField
        tfOutputFolder.addActionListener(new ActionListener() {
            //capturer un événement sur le JTextField
            public void actionPerformed(ActionEvent e) {
                //récupérer et afficher le contenu de JTextField dans la console
                Utils.print("Nouveau répertoire de destination : " + tfOutputFolder.getText());

                //Crée un nouveau répertoire s'il n'existe pas déjà
                createDirectory();
            }
        });
        JCheckBox[] printLevels = new JCheckBox[4];
        JCheckBox[] logLevels = new JCheckBox[4];
        String[] levelNames = {"INFO", "DEBUG", "ERROR", "SYNTAX"};
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 1;
        configPane.add(new JLabel("Print :"), constraints);
        constraints.gridy = 2;
        configPane.add(new JLabel("Log :"), constraints);
        for(int i=0; i<4; i++) {
            printLevels[i] = new JCheckBox(levelNames[i]);
            logLevels[i] = new JCheckBox(levelNames[i]);
            printLevels[i].setSelected(Utils.isLevelPrinted(i));
            logLevels[i].setSelected(Utils.isLevelLogged(i));
            final int level = i;
            printLevels[i].addItemListener(e -> Utils.setLevelPrinted(level, printLevels[level].isSelected()));
            logLevels[i].addItemListener(e -> Utils.setLevelLogged(level, logLevels[level].isSelected()));
            constraints.gridx = 1+i;
            constraints.gridy = 1;
            configPane.add(printLevels[i], constraints);
            constraints.gridy = 2;
            configPane.add(logLevels[i], constraints);
        }
        constraints.gridx = 5;
        constraints.gridy = 1;
        configPane.add(new JLabel("Journal :"), constraints);
        tfLogFile = new JTextField("log.txt", 16);
        constraints.gridx = 6;
        configPane.add(tfLogFile, constraints);
        saveLogButton = new JButton("Sauvegarder");
        saveLogButton.addActionListener(e -> Utils.saveLog(tfLogFile.getText()));
        constraints.gridy = 2;
        constraints.gridx = 5;
        constraints.gridwidth = 2;
        configPane.add(saveLogButton, constraints);
        
        textOutput = new JTextArea();
        textOutput.setRows(15);
        textOutput.setEditable(false);
        System.setOut(new PrintStreamCapturer(textOutput, System.out));
        System.setErr(new PrintStreamCapturer(textOutput, System.err, "[ERROR]"));
        //Contient les barres de chargement
        JPanel progressPane = new JPanel();
        //Ajoute un espace au dessus
        progressPane.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        // Barre de chargement Fate
        progressBarFate = new JProgressBar();
        progressBarFate.setMinimum(0);
        progressBarFate.setStringPainted(true);
        progressBarFate.setForeground(new Color(5, 67, 149));
        progressBarFate.setBorderPainted(false);
        // Barre de chargement UBW
        progressBarUBW = new JProgressBar();
        progressBarUBW.setMinimum(0);
        progressBarUBW.setStringPainted(true);
        progressBarUBW.setForeground(new Color(134, 18, 5));
        progressBarUBW.setBorderPainted(false);
        // Barre de chargement HF
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
                connectionButton.setText("Téléchargement");
                Utils.print("Connecté à l'API Google Drive.\n");
            } catch (GeneralSecurityException | IOException e1) {
                Utils.print(e1.toString(), Utils.ERROR);
            }

            // Démarre le téléchargement de tous les fichiers de script dans différents Thread
            FetchingThread ftFate = new FetchingThread(googleAPI, progressBarFate, "Fate");
            FetchingThread ftUBW = new FetchingThread(googleAPI, progressBarUBW, "Unlimited Blade Works");
            FetchingThread ftHF = new FetchingThread(googleAPI, progressBarHF, "Heavens Feel");
            FetchingThread ftStatuts = new FetchingThread(googleAPI, progressBarHF, "Statuts");

            ftFate.setOutputFolder(tfOutputFolder.getText());
            ftUBW.setOutputFolder(tfOutputFolder.getText());
            ftHF.setOutputFolder(tfOutputFolder.getText());
            ftStatuts.setOutputFolder(tfOutputFolder.getText());
            tfOutputFolder.setEditable(false);
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
        this.setTitle("Fate/Stay Night Packager - 0.9");
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setSize(600, 374);

        //Barres de chargement
        progressPane.add(new JLabel(" Fate ", JLabel.CENTER));
        progressPane.add(new JLabel(" Unlimited Blade Works ", JLabel.CENTER));
        progressPane.add(new JLabel(" Heaven's Feel + Statuts ", JLabel.CENTER));
        progressPane.add(progressBarFate, BorderLayout.CENTER);
        progressPane.add(progressBarUBW, BorderLayout.CENTER);
        progressPane.add(progressBarHF, BorderLayout.CENTER);
        progressPane.setLayout(new GridLayout(2,3));

        JScrollPane scrollPane = new JScrollPane(textOutput);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);

        this.add(configPane, BorderLayout.NORTH);
        this.add(progressPane, BorderLayout.SOUTH);
        this.add(scrollPane);

        this.setLocationRelativeTo(null);
        this.setVisible(true);
    }

    /**
     * Crée un répertoire du nom du JTextField si celui-ci n'existe pas déjà
     */
    public void createDirectory() {
        String directoryName = tfOutputFolder.getText();

        File directory = new File(directoryName);
        if (!directory.exists()){
            Utils.print("Répertoire \"" + directoryName + "\" inexistant donc créé\n");
            directory.mkdir();
        }
    }

    public static void main(String[] args) {
        Main main = new Main();
    }

}
