import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DuplicateFileRemover extends JFrame {

    private static final int BLOCK_SIZE = 65536;
    
    // GUI Components
    private JTextField pathField;
    private JTextArea logArea;
    private JButton scanButton;

    public DuplicateFileRemover() {
        // --- Set up the Main Window ---
        setTitle("Duplicate File Remover");
        setSize(700, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Centers the window on screen
        setLayout(new BorderLayout(10, 10));

        // --- Top Panel (Browse & Scan) ---
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));

        pathField = new JTextField();
        pathField.setEditable(false); // Make it read-only, force user to use Browse button

        JButton browseButton = new JButton("Browse Folder...");
        browseButton.addActionListener(e -> selectFolder());

        scanButton = new JButton("Scan for Duplicates");
        scanButton.setEnabled(false); // Disabled until a folder is selected
        scanButton.addActionListener(e -> startScan());

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(browseButton);
        buttonPanel.add(scanButton);

        topPanel.add(new JLabel("Selected Folder:"), BorderLayout.NORTH);
        topPanel.add(pathField, BorderLayout.CENTER);
        topPanel.add(buttonPanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        // --- Center Panel (Log Output) ---
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(10, 10, 10, 10),
                BorderFactory.createTitledBorder("Scan Logs")
        ));

        add(scrollPane, BorderLayout.CENTER);
    }

    // Helper method to safely write to the GUI log area
    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            // Auto-scroll to the bottom
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void selectFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); // Only allow folders
        chooser.setDialogTitle("Select Folder to Scan");

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = chooser.getSelectedFile();
            pathField.setText(selectedFolder.getAbsolutePath());
            scanButton.setEnabled(true);
            log("Selected folder: " + selectedFolder.getAbsolutePath());
            log("Ready to scan.");
        }
    }

    private void startScan() {
        String targetDirectory = pathField.getText();
        scanButton.setEnabled(false); // Disable button while scanning
        log("\n--- Starting Scan ---");
        
        // Run the heavy scanning process in a new thread so the GUI doesn't freeze!
        new Thread(() -> {
            findAndManageDuplicates(targetDirectory);
            SwingUtilities.invokeLater(() -> scanButton.setEnabled(true));
        }).start();
    }

    // --- Core Logic (Adapted for GUI) ---
    
    private void collectAllFiles(File dir, List<File> allFiles) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    allFiles.add(file);
                } else if (file.isDirectory()) {
                    collectAllFiles(file, allFiles);
                }
            }
        }
    }

    private String getFileChecksum(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] byteArray = new byte[BLOCK_SIZE];
            int bytesCount;
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
        }
        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte aByte : bytes) {
            sb.append(String.format("%02x", aByte));
        }
        return sb.toString();
    }

    private void findAndManageDuplicates(String targetDirectory) {
        File folder = new File(targetDirectory);
        List<File> allFiles = new ArrayList<>();
        
        log("Mapping directory tree...");
        collectAllFiles(folder, allFiles);

        if (allFiles.isEmpty()) {
            log("The directory is empty or could not be read.");
            return;
        }
        log("Found " + allFiles.size() + " total files to check.");

        Map<Long, List<File>> filesBySize = new HashMap<>();
        for (File file : allFiles) {
            long size = file.length();
            filesBySize.computeIfAbsent(size, k -> new ArrayList<>()).add(file);
        }

        Map<String, File> hashMap = new HashMap<>();
        List<File> duplicatesFound = new ArrayList<>();

        log("Calculating hashes for potential duplicates...");
        for (List<File> fileGroup : filesBySize.values()) {
            if (fileGroup.size() > 1) {
                for (File file : fileGroup) {
                    try {
                        String fileHash = getFileChecksum(file);
                        if (hashMap.containsKey(fileHash)) {
                            duplicatesFound.add(file);
                            log("Found duplicate: " + file.getName());
                        } else {
                            hashMap.put(fileHash, file);
                        }
                    } catch (Exception e) {
                        log("Error processing file: " + file.getAbsolutePath());
                    }
                }
            }
        }

        if (duplicatesFound.isEmpty()) {
            log("Scan complete. No duplicate files found.");
            return;
        }

        log("Scan complete. " + duplicatesFound.size() + " duplicates found.");

        // --- GUI Pop-Up for Confirmation ---
        int response = JOptionPane.showConfirmDialog(
                this,
                "Found " + duplicatesFound.size() + " duplicate file(s).\nDo you want to permanently delete them?",
                "Confirm Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (response == JOptionPane.YES_OPTION) {
            log("\n--- Deleting Files ---");
            int deletedCount = 0;
            for (File f : duplicatesFound) {
                if (f.delete()) {
                    log("Deleted: " + f.getAbsolutePath());
                    deletedCount++;
                } else {
                    log("Failed to delete: " + f.getAbsolutePath() + " (May be in use)");
                }
            }
            log("Successfully deleted " + deletedCount + " files.");
            JOptionPane.showMessageDialog(this, "Deleted " + deletedCount + " files successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
        } else {
            log("\nDeletion cancelled by user. No files were harmed.");
        }
    }

    public static void main(String[] args) {
        // Launch the GUI safely
        SwingUtilities.invokeLater(() -> {
            DuplicateFileRemover gui = new DuplicateFileRemover();
            gui.setVisible(true);
        });
    }
}