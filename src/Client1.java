import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Client1 {

    public static void main(String[] args) {

        Socket clientSocket;
        final BufferedReader in;
        final PrintWriter out;
        final Scanner sc = new Scanner(System.in);//pour lire à partir du clavier
        final boolean[] FIRST_TIME = {true};

        try {
            /*
             * les informations du serveur ( port et adresse IP ou nom d'hote
             * 127.0.0.1 est l'adresse local de la machine
             */
            clientSocket = new Socket( args[0], Integer.parseInt(args[1]));

            //flux pour envoyer
            out = new PrintWriter(clientSocket.getOutputStream());
            //flux pour recevoir
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            Thread envoyer = new Thread(new Runnable() {
                String msg;
                @Override
                public void run() {
                    while(true){

                        msg = sc.nextLine();
                        out.println(msg);
                        out.flush();
                    }
                }
            });
            envoyer.start();

            Thread recevoir = new Thread(new Runnable() {
                String msg;
                @Override
                public void run() {

                    try {
                        msg = in.readLine();
                        while(msg!=null){
                            if(! msg.equals("##Transmition-finish##")) {
                                System.out.println("Serveur : " + msg);

                                if (msg.equals("$$download-mode-on$$")) {
                                    System.out.println("Début du téléchargement");
                                    download(in);
                                }

                                //initialisation LIST
                                if (FIRST_TIME[0]) {
                                    FIRST_TIME[0] = false;
                                    out.println("LIST");
                                    out.flush();
                                }
                            }
                                msg = in.readLine();
                            }
                            System.out.println("Serveur déconecté");
                            out.close();
                            clientSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                private void download(BufferedReader in ) throws IOException {
                    //on récupère le nom du fichier
                    msg = in.readLine();
                    String nameFile;
                    if(msg.equals("$$download-title-file$$")) {
                        nameFile = in.readLine();
                        msg = in.readLine();
                        //@todo à retirer
                        System.out.println("test : nameFile: " + nameFile);
                        //Vérifier que le fichier n'est pas déjà existant ? ou pas #consigneTP
                        File file = createFile(nameFile);
                        //On créé un stock
                        PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8);
                        //on boucle tant que msg != $$download-mode-on$$
                        while (!msg.equals("$$download-mode-off$$")){
                            writer.println(msg);
                            msg = in.readLine();
                        }
                        writer.close();
                    }




                }

                //  une fonction qui créée un nouveau fichier (et l'ajoute à la map),
                public File createFile(String namefile) throws IOException {
                    File file = new File("ClientsFiles/" + namefile);
                    if (file.createNewFile())
                        System.out.println("File created");
                    else
                        System.out.println("File already exists");
                    return file;
                }


                // une fonction qui permet de lire le contenu d'une socket pour écraser un fichier
                public void readAndPast(String fileName, BufferedReader in) throws IOException {
                        File file = new File("ClientsFiles/" + fileName); //récupération fichier
                        FileHandle fileH = new FileHandle(file);
                        Scanner scanner = new Scanner(in);  // Create a Scanner object
                        FileHandle.OperationStatus STATUS = fileH.replaceFile(scanner); // écrasement du fichier
                        System.out.println("STATUS: " + STATUS);

                }

                // Ecriture personnalisé
                public void writeFile(File file, String string){


                }
            });
            recevoir.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
