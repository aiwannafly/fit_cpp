package torrent.client.downloader;

import be.christophedetroyer.torrent.Torrent;
import torrent.Constants;
import torrent.client.FileManager;
import torrent.client.exceptions.BadTorrentFileException;
import torrent.client.exceptions.NoSeedsException;

import java.util.*;
import java.util.concurrent.*;

import static torrent.client.downloader.MultyDownloadManager.Status.FINISHED;

public class MultyDownloadManager {
    private final ExecutorService leechPool = Executors.newFixedThreadPool(
            Constants.DOWNLOAD_MAX_THREADS_COUNT);
    private final FileManager fileManager;
    private final String peerId;
    private final ArrayList<String> removalList = new ArrayList<>();
    private final Map<String, DownloadFileManager> downloadManagers = new HashMap<>();
    private final ExecutorService downloader = Executors.newSingleThreadExecutor();
    private final CompletionService<Status> downloadService =
            new ExecutorCompletionService<>(downloader);
    private final Queue<String> stoppedTorrents = new ArrayDeque<>();
    private final Queue<DownloadFileManager> newTorrents = new ArrayBlockingQueue<>(100);
    private boolean downloading = false;

    enum Status {
        FINISHED, NOT_FINISHED
    }

    public MultyDownloadManager(FileManager fileManager, String peerId) {
        this.fileManager = fileManager;
        this.peerId = peerId;
    }

    public void addTorrent(Torrent torrent, int[] peerPorts) throws NoSeedsException {
        String torrentFileName = torrent.getName() + Constants.POSTFIX;
        DownloadFileManager downloadFileManager = new DownloadFileManager(torrent,
                fileManager, peerId, peerPorts, leechPool);
        if (!downloading) {
            downloadManagers.put(torrentFileName, downloadFileManager);
        } else {
            newTorrents.add(downloadFileManager);
        }
    }

    public void run() {
        if (downloading) {
            return;
        }
        downloading = true;
        downloadService.submit(() -> {
            while (!downloadManagers.isEmpty()) {
                while (!newTorrents.isEmpty()) {
                    DownloadFileManager downloadFileManager = newTorrents.remove();
                    String torrentFileName = downloadFileManager.getTorrentFile().getName() +
                            Constants.POSTFIX;
                    downloadManagers.put(torrentFileName, downloadFileManager);
                }
                for (String torrentFileName: downloadManagers.keySet()) {
                    if (stoppedTorrents.contains(torrentFileName)) {
                        continue;
                    }
                    DownloadFileManager downloadFileManager = downloadManagers.get(torrentFileName);
                    DownloadFileManager.Result result = downloadFileManager.downloadNextPiece();
                    if (result.status == DownloadFileManager.Status.FINISHED) {
                        removalList.add(torrentFileName);
                    }
                }
                for (String torrent: removalList) {
                    downloadManagers.remove(torrent).shutdown();
                }
                removalList.clear();
            }
            downloading = false;
            return FINISHED;
        });
    }

    public void stop(String torrentFileName) throws BadTorrentFileException {
        if (downloadManagers.containsKey(torrentFileName)) {
            stoppedTorrents.add(torrentFileName);
            return;
        }
        throw new BadTorrentFileException("File " + torrentFileName + " is not downloaded");
    }

    public void resume(String torrentFileName) throws BadTorrentFileException {
        if (downloadManagers.containsKey(torrentFileName)) {
            stoppedTorrents.remove(torrentFileName);
            return;
        }
        throw new BadTorrentFileException("File " + torrentFileName + " is not downloaded");
    }

    public void shutdown() {
        leechPool.shutdown();
        for (String torrentFileName: downloadManagers.keySet()) {
            downloadManagers.get(torrentFileName).shutdown();
        }
        downloader.shutdown();
    }
}
