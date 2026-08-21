import java.io.*;

public class Main
{
    // Project lineage: Project 2 of 2
    // Same author, same project line
    // Prior project: Captain Marvell (retired/cleaned)
    // Current project: Tiamat — Level 9 Adventurer

    public static final String PROJECT_NAME = "Tiamat";
    public static final int PROJECT_SEQUENCE = 2;
    public static final int PROJECT_TOTAL = 2;
    public static final String PROJECT_LINEAGE = "same-author";

    public static void main(String[] args) throws IOException
    {
        String baseDir = args.length > 0 ? args[0] : ".";
        String configPath = "source/configuration/search-engines.config";

        System.out.println("=== Tiamat System ===");
        System.out.println("    Project " + PROJECT_SEQUENCE + " of " + PROJECT_TOTAL
            + " | Lineage: " + PROJECT_LINEAGE);
        System.out.println();

        // Run search engine client
        System.out.println("[1] Running Search Engine Client...\n");
        SearchEngineClient client = new SearchEngineClient(configPath, baseDir);
        client.searchAll();

        // Run AI module — notify project context
        System.out.println("[2] Running AI Module...\n");
        AIModule ai = new AIModule(baseDir);
        ai.setProjectContext(PROJECT_NAME, PROJECT_SEQUENCE, PROJECT_TOTAL, PROJECT_LINEAGE);
        ai.processIncomingFiles();

        System.out.println("\n=== Complete ===");
    }
}
