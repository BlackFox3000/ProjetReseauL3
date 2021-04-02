public class Serveur {

    public String ip;
    public Integer port;

    public Serveur(String ip, Integer port) {
        this.ip = ip;
        this.port = port;
    }

    public boolean egal(Serveur serveur) {
        return this.ip.equals(serveur.ip) && this.port.equals(serveur.port);
    }
}
