package fr.bloomenetwork.fatestaynight.packager;

import java.awt.BorderLayout;
import java.awt.GridLayout;
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
    private JProgressBar progressBar;
    
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
                //setOutputFolder(outputFolderTextField.getText());

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
        progressBar = new JProgressBar();
        progressBar.setMinimum(0);
        progressBar.setStringPainted(true);
        
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

            // Démarre le téléchargement de tous les fichiers de script
            FetchingThread ft = new FetchingThread(googleAPI, progressBar);
            ft.setOutputFolder(this.outputFolder);
            Thread t = new Thread(ft);
            t.start();
            // Crée le répertoire si celui-ci n'existe pas
            createDirectory();
        });
        
        //Mise en page de la fenêtre 
        this.setTitle("Fate/Stay Night Packager");
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setSize(600, 374);
        topPane.add(new JLabel(" API Google + début : "));
        topPane.add(connectionButton);
        topPane.add(new JLabel(" Répertoire de sortie : "));
        topPane.add(outputFolderTextField);
        topPane.setLayout(new GridLayout(2, 2));    
        
        this.add(topPane, BorderLayout.NORTH);
        this.add(progressBar, BorderLayout.CENTER);
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
