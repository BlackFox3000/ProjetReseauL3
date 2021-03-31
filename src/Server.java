import java.net.*;
import java.io.*;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

class Server {

    private static String PATH = "Documents";

    /* Démarrage et délégation des connexions entrantes */
    public void demarrer(int port) {
        ServerSocket ssocket; // socket d'écoute utilisée par le serveur

        System.out.println("Lancement du serveur sur le port " + port);
        try {
            ssocket = new ServerSocket(port);
            ssocket.setReuseAddress(true); /* rend le port réutilisable rapidement */

            while (true) {
                (new Thread(new Handler(ssocket.accept()))).start();
            }
        } catch (IOException ex) {
            System.out.println("Arrêt anormal du serveur.");
            return;
        }
    }

    public static void main(String[] args) {
        int argc = args.length;
       Server serveur;

        /* Traitement des arguments */
        if (argc == 1) {
            try {
                serveur = new Server();
                serveur.demarrer(Integer.parseInt(args[0]));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Usage: java EchoServer port");
        }
        return;
    }

    /*
       echo des messages reçus (le tout via la socket).
       NB classe Runnable : le code exécuté est défini dans la
       méthode run().
    */
    class Handler implements Runnable {

        Socket socket;
        PrintWriter out;
        BufferedReader in;
        InetAddress hote;
        int port;

        Handler(Socket socket) throws IOException {
            this.socket = socket;
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            hote = socket.getInetAddress();
            port = socket.getPort();
        }

        public void run() {
            String tampon;
            long compteur = 0;

            try {
                /* envoi du message d'accueil */
                out.println("Bonjour " + hote + "! (vous utilisez le port " + port + ")");
                ManagementFiles managementFiles = new ManagementFiles(PATH, out);

                do {
                    /* Faire echo et logguer */

                    tampon = in.readLine();

                    if (tampon != null) {
                        compteur++;
                        /* log */
                        System.err.println("[" + hote + ":" + port + "]: " + compteur + ":" + tampon);
                        /* echo vers le client */
                        //out.println("> " + tampon);

                        String[] lines = tampon.split(" ");

                        if (lines[0].equals("HELP")) {
                            out.println("==================== HELP ====================");
                            out.println("HELP: list les commandes disponibles");
                            out.println("LIST: permet de demander la liste des fichiers du serveur");
                            out.println("GET [filemane]: permet de télécharger le fichier \"filename\"");
                            out.println("READ [filemane]: permet de lire le fichier \"filename\"");
                            out.println("CREATE [filemane]: permet de créer un nouveau fichier vide nommé \"filename\"");
                            out.println("WRITE [filemane]: permet d'uploader une nouvelle version du fichier \"filename\"");
                            out.println("DELETE [filemane]: permet de supprimer le fichier \"filename\"");
                            out.println("==================== ==== ====================");
                        }
                        /**
                         *     LISTE
                         */
                        else if (lines[0].equals("LIST")) {
                            File file = new File(PATH);
                            File[] files = file.listFiles();
                            int page = 1, total = (Integer) files.length/5;
                            if(lines.length == 2) {
                                page = Integer.parseInt(lines[1]);
                                if (page > total+1)
                                    page = total;
                            }
                            int max = 5+5*(page-1);
                            if(max>files.length)
                                max = files.length;
                            out.println("page:"+page+" max:"+max+" total:"+total);
                            File[] subFiles = Arrays.copyOfRange(files,0+5*(page-1),max);
                            out.println(" ==== List "+page+"/"+total+" ===");

                            assert files != null;
                            //out.println("WIP ");
                            for (File doc : subFiles)
                                out.println(doc.getName());

                            //managementFiles.list();
                        }
                        else if (lines[0].equals("GET")) {

                            if( lines.length!=2)
                                out.println("Commande incomplete: GET [filemane]");
                            else {
                                String fileName = (String) lines[1];
                                managementFiles.upload(fileName);
                            }

                        }else if (lines[0].equals("READ")) {

                            if( lines.length!=2)
                                out.println("Commande incomplete: READ [filemane]");
                            else {
                                String fileName = (String) lines[1];
                                managementFiles.readTOSocket(fileName);
                            }

                        }

                        else if (lines[0].equals("CREATE")) {
                            out.println("WIP create");
                            if(lines.length !=2)
                                out.println("Commande incomplete : CREATE [filename]");
                            else {
                                String fileName = (String) lines[1];
                                managementFiles.createFile(fileName);

                            }
                        }

                        else if (lines[0].equals("WRITE")) {
                            out.println("WIP write");
                            if( lines.length!=2)
                                out.println("Commande incomplete: WRITE [filemane]");
                            else {
                                String fileName = (String) lines[1];
                                managementFiles.readAndPast(fileName, in);
                            }
                        }

                        else if (lines[0].equals("DELETE")) {
                            out.println("WIP delete");
                            if(lines.length < 2){
                                out.println("DELETE [filename]");
                            }
                            else{
                                String fileName = (String) lines[1];
                                managementFiles.deleteFile(fileName);
                            }
                        }

                        else {
                            out.println("ERROR : unknown request");
                            out.println("ERROR : tapper HELP pour plus d'informations");
                        }

                    } else {
                        break;
                    }
                } while (true);

                /* le correspondant a quitté */
                in.close();
                out.println("Au revoir...");
                out.close();
                socket.close();

                System.err.println("[" + hote + ":" + port + "]: Terminé...");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    class ManagementFiles{
        public ConcurrentHashMap<String, FileHandle> concurrentHashMap  = new ConcurrentHashMap<>() ;
        public PrintWriter out;
        //<String,FileHandle>


//        public ManagementFiles(ConcurrentHashMap concurrentHashMap) {
//            this.concurrentHashMap = concurrentHashMap;
//        }

        /**
         * initialise une map et fct des fichier présent dans le dossier "document"
         * @param path
         * @param out
         */
        public ManagementFiles(String path, PrintWriter out){
            this.out = out;
            File doc = new File(path);
            File[] files = doc.listFiles();
            assert files != null;
            for (File file : files) {
                String namefile = file.getName();
                FileHandle fileHandle = new FileHandle(file);
                System.out.println("name:"+namefile);
                this.concurrentHashMap.put(namefile, fileHandle);
            }
        }

        /**
         * une fonction qui permet de vérifier qu'un fichier est présent
         * @param fileName
         * @return
         */
        public boolean isExist(String fileName ){ return this.concurrentHashMap.containsKey(fileName); }

        /**
         * une fonction qui permet de lire un fichier et de l'envoyer à une socket,
         * @param fileName
         */
        public void readTOSocket(String fileName){
            if(  this.isExist(fileName)) {
                FileHandle file = (FileHandle) this.concurrentHashMap.get(fileName);
                //envoyer a un socket
                file.readFile(this.out);

            }
        }

        /**
         * une fonction qui permet d'empaqueter l'envoie du contenu d'un fichier,
         * @param fileName
         */
        public void upload(String fileName){
            if(  this.isExist(fileName)) {
                FileHandle file = (FileHandle) this.concurrentHashMap.get(fileName);
                //activation du mode
                this.out.println("$$download-mode-on$$");
                //envoie du titre
                this.out.println("$$download-title-file$$");
                this.out.println(fileName);
                //envoyer a un socket
                file.readFile(this.out);
                //arret
                this.out.println("$$download-mode-off$$");
                this.out.println("Téléchargement terminé");
            }
        }




        // une fonction qui permet de lire le contenu d'une socket pour écraser un fichier (n'oubliez pas d'employer les fonctions de FileHandle),
        public void readAndPast(String fileName, BufferedReader in) throws IOException {
            out.println("Rédiger votre document:");
            if(  this.isExist(fileName)) {
                FileHandle file = (FileHandle) this.concurrentHashMap.get(fileName);
                Scanner scanner = new Scanner(in);  // Create a Scanner object
                FileHandle.OperationStatus STATUS = file.replaceFile(scanner);
                System.out.println("STATUS: " + STATUS);
            }


        }


        //  une fonction qui créée un nouveau fichier (et l'ajoute à la map),
        public void createFile(String namefile) throws IOException {
            File file = new File("Documents/" + namefile);
            FileHandle fileHandle = new FileHandle(file);
            this.concurrentHashMap.put(namefile, fileHandle);
            if (file.createNewFile())
                System.out.println("File created");
            else
                System.out.println("File already exists");
        }


        // une fonction qui supprime un fichier (et qui l'enlève de la map).
        public void deleteFile(String namefile){
            File file = new File("Documents/" + namefile);
            FileHandle fileHandle = new FileHandle(file);
            fileHandle.delete();
            this.concurrentHashMap.remove(namefile);
        }

        //défaut
        public void list() {
            Enumeration keys = this.concurrentHashMap.keys();
            String key;
            if( keys == null )
                out.println("Aucun fichier existant");
            else
                do{
                    key= (String) keys.nextElement();
                    out.println(key);
                }while(key!=null);
        }
    }
}
