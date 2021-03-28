import java.io.*;
import java.nio.file.Files;
import java.util.Scanner;

/**
 * Cette classe gère un fichier de façon thread safe. Elle permet à
 * plusieurs thread de lire le fichier en même temps. Si un thread demande une édition du fichier, FileHandle
 * bloquera les demandes suivantes de lecture ou d'écriture, attendra que les lectures courantes sont terminées,
 * puis permettra l'édition. De façon similaire dès qu'un fichier est indiqué "détruit", plus aucune autre action
 * n'est permie.
 */
public class FileHandle {
    private final File file;
    private FileHandle.State state;
    private int readers_count;
    private boolean is_writing;

    /**
     * Enumération qui contient les différents résultats que peuvent renvoyer certaines fonctions de cette classe.
     * Tester le résultat de retour de ces fonctions permet de savoir en détail ce qui s'est mal passé lorsqu'une
     * erreur intervient, et permet de savoir lorsque tout s'est bien passé (OK).
     */
    public enum OperationStatus {
        OK,
        ERROR_FILE_DELETED,
        ERROR_FILE_DOES_NOT_EXIST,
        ERROR_INTERRUPTED,
        ERROR_FILE_STREAM,
        ERROR_DELETION
    }

    /**
     * Énumération privée des états interne de la classe.
     */
    private enum State {
        AVAILABLE,
        READING,
        MARKED_FOR_MODIFICATION,
        MODIFYING,
        MARKED_FOR_DELETION
    }

    /**
     * Constructeur. Prend en paramètre le chemin vers le fichier. Suppose que le fichier existe, il faut donc le créer
     * auparavant.
     * @param file Le fichier qui sera pris en charge par l'objet FileHandle
     */
    public FileHandle(File file) {
        this.file = file;
        setState(FileHandle.State.AVAILABLE);
        readers_count = 0;
        is_writing = false;
    }

    /**
     * Fonction interne de changement d'état. Privée
     * @param state Le nouvel état
     */
    private void setState(FileHandle.State state) {
        System.out.println("File " + file.getName() + " going to state " + state.name());
        this.state = state;
    }

    /**
     * @return La taille du fichier sur le disque
     */
    public long getSize() {
        return file.length();
    }

    /**
     * Lit le fichier, et l'écrit ligne par ligne dans le PrintWriter donnée en paramètre.
     * Cette fonction est possiblement bloquante si une édition est en cours, et peut également
     * être annulée si le fichier a été marqué pour suppression par un autre thread.
     * @param writer le printwriter utilisé pour écrire le fichier.
     * @return le résultat de l'opération : OK si tout va bien, ERROR_FILE_DELETED si le fichier n'existe plus,
     * ERROR_FILE_STREAM s'il s'est trouvé impossible d'ouvrir le fichier, ERROR_INTERRUPTED si la fonction a été
     * interrompue pendant son attente.
     */
    public OperationStatus readFile(PrintWriter writer) {
        // First synchronize to wait and check that the road is clear
        synchronized (this) {
            try {
                // If the file is readable, we proceed
                while (state != FileHandle.State.AVAILABLE && state != FileHandle.State.READING) {
                    // If the file is getting deleted, we abort.
                    if (state == FileHandle.State.MARKED_FOR_DELETION ) {
                        System.err.println("Read cancelled : file is deleted");
                        return OperationStatus.ERROR_FILE_DELETED;
                    }

                    // In any other case, the file is getting modified and we wait.
                    this.wait();
                }
            } catch (InterruptedException e) {
                System.err.println("FileHandle Interrupted while waiting...");
                e.printStackTrace();
                return OperationStatus.ERROR_INTERRUPTED;
            }

            // We start reading.
            setState(FileHandle.State.READING);
            readers_count++;
        }


        Scanner scanner = null;
        try {
            scanner = new Scanner(Files.newInputStream(file.toPath()));
        } catch (IOException e) {
            System.out.println("Error while opening file stream");
            e.printStackTrace();
            return OperationStatus.ERROR_FILE_STREAM;
        }

        while (scanner.hasNext()) {
            String line = scanner.nextLine();
            writer.println(line);
        }

        writer.flush();
        scanner.close();

        // Reading is over, we make the file available again
        synchronized (this) {
            readers_count--;
            if(readers_count == 0) {
                // If the file is marked for something, we preserve that mark
                setState(state == FileHandle.State.READING ? FileHandle.State.AVAILABLE : state);
                this.notifyAll();
            }
        }

        return OperationStatus.OK;
    }

    /**
     * Cette fonction remplace le contenu du fichier par ce qui est fournis par le scanner donnée en paramètre.
     * L'écriture se termine lorsque le scanner n'a plus rien à donner ; cette écriture se termine donc
     * avec le stream du scanner. Si vous écrivez à partir de System.in, ctrl+D ou ctrl+C permettent de forcer la
     * fermeture du stream.
     *
     * @param scanner Ce scanner est utilisé comme la source d'information pour remplir le fichier.
     * @return
     * Cette fonction renvoie le status de son opération, qui peut être :
     * - ERROR_FILE_DELETED : le fichier a été marqué détruit par un autre thread
     * - ERROR_INTERRUPTED : le thread a été interrompu pendant qu'il attendait son tour
     * - ERROR_FILE_DOES_NOT_EXIST : le fichier n'existe pas sur le disque (et on ne peut donc pas écrire dedans)
     * - ERROR_FILE_STREAM : erreur pendant l'écriture ou la fermeture du fichier
     * - OK : l'opération s'est bien déroulée
     */
    public FileHandle.OperationStatus replaceFile(Scanner scanner) {
        // First synchronize to wait and check that the road is clear
        synchronized (this) {
            try {
                for(;;)  {
                    // If the file is available, we proceed
                    if( state == FileHandle.State.AVAILABLE ) {
                        setState(FileHandle.State.MODIFYING);
                        break;
                    }
                    // If the file is getting deleted, we abort
                    if (state == FileHandle.State.MARKED_FOR_DELETION ) {
                        System.err.println("Write cancelled : file is deleted");
                        return FileHandle.OperationStatus.ERROR_FILE_DELETED;
                    }

                    // If the file is being read, we reserve for modification and wait
                    if( state == FileHandle.State.READING ) {
                        setState(FileHandle.State.MARKED_FOR_MODIFICATION);
                        // We wait until all readers stopped
                        while(readers_count > 0) this.wait();
                        setState(FileHandle.State.MODIFYING);
                        break;
                    }

                    // In any other case, another thread is modifying or is reserving modification. We wait
                    this.wait();
                }
            } catch (InterruptedException e) {
                System.err.println("FileHandle Interrupted while waiting...");
                e.printStackTrace();
                return FileHandle.OperationStatus.ERROR_INTERRUPTED;
            }

            // We start writing.
            is_writing = true;
        }

        FileWriter writer = null;
        try {
            writer = new FileWriter(file);
        } catch (IOException e) {
            System.err.println("File " + file.getPath() + " does not exist");
            e.printStackTrace();
            return FileHandle.OperationStatus.ERROR_FILE_DOES_NOT_EXIST;
        }
        while(scanner.hasNext()) {
            String line = scanner.nextLine();
            try {
                writer.write(line + "\n");
            } catch (IOException e) {
                System.err.println("Error while writing to file " + file.getName());
                e.printStackTrace();
                return FileHandle.OperationStatus.ERROR_FILE_STREAM;
            }
        }
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            System.err.println("Error while closing the file " + file.getName());
            e.printStackTrace();
            return FileHandle.OperationStatus.ERROR_FILE_STREAM;
        }

        // Writing stopped, we make the file available again
        synchronized (this) {
            setState(state == FileHandle.State.MODIFYING ? FileHandle.State.AVAILABLE : state);
            is_writing = false;
            this.notifyAll();
        }
        return FileHandle.OperationStatus.OK;
    }

    /**
     * Cette fonction demande à l'objet FileHandle de supprimer le fichier du disque.
     * Après cette opération, l'objet FileHandle ne doit plus être utilisé car le fichier correspondant n'existe
     * plus. Il faudra recréer le fichier et créer un nouveau FileHandle.
     * @return
     * Cette fonction renvoie le status de son opération, qui peut être :
     * - ERROR_INTERRUPTED : le thread a été interrompu pendant qu'il attendait son tour
     * - ERROR_DELETION : erreur pendant la suppression du fichier
     * - OK : l'opération s'est bien déroulée
     */
    public FileHandle.OperationStatus delete() {
        synchronized (this) {
            if( state == FileHandle.State.MARKED_FOR_DELETION ) {
                System.err.println("FileHandle : double delete on file " + file.getName());
                return FileHandle.OperationStatus.OK;
            }
            System.out.println("bla");
            setState(FileHandle.State.MARKED_FOR_DELETION);

            try {
                // We wait for the end of edition
                while(is_writing) this.wait();
            } catch (InterruptedException e) {
                System.err.println("FileHandle Interrupted while waiting...");
                e.printStackTrace();
                return FileHandle.OperationStatus.ERROR_INTERRUPTED;
            }
        }

        return file.delete() ? FileHandle.OperationStatus.OK : FileHandle.OperationStatus.ERROR_DELETION;
    }
}