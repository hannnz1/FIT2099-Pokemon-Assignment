package game.agent.persistence;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Map;
/** Local development fallback. Each checkpoint is forced and atomically replaced. */
public final class FileWorldStore implements WorldStore {
    private final Path directory;
    private boolean closed;
    public FileWorldStore(Path directory) {
        if(directory==null) throw new StoreException(StoreException.Code.IO_ERROR);
        this.directory=directory.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.directory);
            if(Files.isSymbolicLink(this.directory) || !Files.isDirectory(this.directory,LinkOption.NOFOLLOW_LINKS))
                throw new IOException();
        } catch(IOException | SecurityException e) { throw new StoreException(StoreException.Code.IO_ERROR); }
    }
    public synchronized void save(String owner,Map<String,Object> checkpoint) {
        ensureOpen(); String key=CheckpointDocument.ownerKey(owner); byte[] bytes=CheckpointDocument.encode(key,checkpoint);
        Path temporary=null;
        try {
            temporary=Files.createTempFile(directory,"pokemon-agent-",".tmp");
            try(FileChannel channel=FileChannel.open(temporary,StandardOpenOption.WRITE)) {
                ByteBuffer buffer=ByteBuffer.wrap(bytes); while(buffer.hasRemaining()) channel.write(buffer); channel.force(true);
            }
            Files.move(temporary,directory.resolve(key+".json"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } catch(IOException | SecurityException e) { throw new StoreException(StoreException.Code.IO_ERROR); }
        finally { if(temporary!=null) try { Files.deleteIfExists(temporary); } catch(IOException ignored) {} }
    }
    public synchronized Map<String,Object> load(String owner) {
        ensureOpen(); String key=CheckpointDocument.ownerKey(owner); Path path=directory.resolve(key+".json");
        try {
            if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS)) return null;
            if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)) throw new StoreException(StoreException.Code.CORRUPT);
            if(Files.size(path)>CheckpointDocument.MAX_BYTES) throw new StoreException(StoreException.Code.TOO_LARGE);
            try(InputStream input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192]; int count;
                while((count=input.read(buffer))!=-1) {
                    if(out.size()+count>CheckpointDocument.MAX_BYTES) throw new StoreException(StoreException.Code.TOO_LARGE);
                    out.write(buffer,0,count);
                }
                return CheckpointDocument.decode(key,out.toByteArray());
            }
        } catch(NoSuchFileException e) { return null; }
        catch(IOException | SecurityException e) { throw new StoreException(StoreException.Code.IO_ERROR); }
    }
    public synchronized void close() { closed=true; }
    private void ensureOpen() { if(closed) throw new StoreException(StoreException.Code.CLOSED); }
}
