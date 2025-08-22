import javax.swing.*;
import java.awt.*;
import java.util.Random;

public class SortingVisualizerUI extends JFrame {

    // UI
    private JComboBox<String> algorithmDropdown, themeDropdown;
    private JSlider arraySizeSlider, speedSlider;
    private JButton startButton, pauseButton, resetButton;
    private JLabel arraySizeLabel, algorithmLabel, speedLabel, statusLabel;
    private DrawPanel centerPanel;

    // Data
    private int[] array;
    private volatile boolean isSorting = false;
    private volatile boolean isPaused  = false;
    private final Object pauseLock = new Object();
    private Thread sortThread;

    // Theme colors
    private Color gradStart = new Color(114, 87, 232);
    private Color gradEnd   = new Color(173, 210, 255);
    private Color barColor  = Color.WHITE;
    private Color hlColor   = new Color(255, 255, 255, 180);

    // Highlight indices
    private volatile int hiA = -1, hiB = -1;

    // Complexity display
    private JTextArea complexityArea;

    public SortingVisualizerUI() {
        setTitle("Sorting Visualizer");
        setSize(1200, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // ── Title
        JPanel titleWrap = new JPanel(new GridLayout(2,1));
        titleWrap.setBackground(new Color(80, 70, 220));
        JLabel title = new JLabel("Sorting Visualizer", SwingConstants.CENTER);
        title.setFont(new Font("Poppins", Font.BOLD, 44));
        title.setForeground(Color.WHITE);
        JLabel sub = new JLabel("Watch algorithms come to life with beautiful animations",
                                SwingConstants.CENTER);
        sub.setFont(new Font("SansSerif", Font.PLAIN, 18));
        sub.setForeground(new Color(235,235,255));
        titleWrap.add(title);
        titleWrap.add(sub);
        add(titleWrap, BorderLayout.NORTH);

        // ── Controls
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 12));
        controls.setBackground(new Color(100, 90, 230));

        algorithmDropdown = new JComboBox<>(new String[]{
                "Bubble Sort", "Selection Sort", "Insertion Sort", "Merge Sort", "Quick Sort"
        });
        algorithmDropdown.setSelectedIndex(0);

        arraySizeSlider = new JSlider(10, 200, 95);
        speedSlider     = new JSlider(1, 100, 66);
        themeDropdown   = new JComboBox<>(new String[]{"Purple Blue", "Pink Orange", "Green Yellow"});

        startButton = new JButton("Start");
        pauseButton = new JButton("Pause");
        resetButton = new JButton("Reset");

        JLabel algLbl = mkLbl("Algorithm:");
        JLabel sizeLbl = mkLbl("Array Size:");
        JLabel spdLbl  = mkLbl("Speed:");
        JLabel thmLbl  = mkLbl("Theme:");

        controls.add(algLbl);  controls.add(algorithmDropdown);
        controls.add(sizeLbl); controls.add(arraySizeSlider);
        controls.add(spdLbl);  controls.add(speedSlider);
        controls.add(thmLbl);  controls.add(themeDropdown);
        controls.add(startButton);
        controls.add(pauseButton);
        controls.add(resetButton);

        add(controls, BorderLayout.BEFORE_FIRST_LINE);

        // ── Center draw panel
        centerPanel = new DrawPanel();
        add(centerPanel, BorderLayout.CENTER);

        // ── Bottom status + complexity
        arraySizeLabel = new JLabel("Array Size: 95", SwingConstants.CENTER);
        algorithmLabel = new JLabel("Algorithm: Bubble Sort", SwingConstants.CENTER);
        speedLabel     = new JLabel("Speed: 66%", SwingConstants.CENTER);
        statusLabel    = new JLabel("Status: Ready", SwingConstants.CENTER);

        Font stF = new Font("Arial", Font.BOLD, 16);

        arraySizeLabel.setFont(stF);
        algorithmLabel.setFont(stF);
        speedLabel.setFont(stF);
        statusLabel.setFont(stF);

        JPanel statusRow = new JPanel(new GridLayout(1,4));
        statusRow.setBackground(new Color(100, 90, 230));
        statusRow.add(arraySizeLabel); statusRow.add(algorithmLabel);
        statusRow.add(speedLabel);     statusRow.add(statusLabel);

        // Complexity area
        complexityArea = new JTextArea();
        complexityArea.setEditable(false);
        complexityArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        complexityArea.setBackground(new Color(80,70,200));
        complexityArea.setForeground(Color.WHITE);
        complexityArea.setText(getComplexity((String) algorithmDropdown.getSelectedItem()));

        JScrollPane scroll = new JScrollPane(complexityArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Algorithm Complexity"));
        scroll.setBackground(new Color(80,70,200));

        JPanel bottom = new JPanel(new GridLayout(2,1));
        bottom.add(statusRow);
        bottom.add(scroll);

        add(bottom, BorderLayout.SOUTH);

        // ── Events
        arraySizeSlider.addChangeListener(e -> {
            arraySizeLabel.setText("Array Size: " + arraySizeSlider.getValue());
            if (!isSorting) generateArray(arraySizeSlider.getValue());
        });
        speedSlider.addChangeListener(e ->
            speedLabel.setText("Speed: " + speedSlider.getValue() + "%")
        );
        algorithmDropdown.addActionListener(e -> {
            String algo = (String) algorithmDropdown.getSelectedItem();
            algorithmLabel.setText("Algorithm: " + algo);
            complexityArea.setText(getComplexity(algo));
        });
        themeDropdown.addActionListener(e -> applyTheme((String) themeDropdown.getSelectedItem()));
        startButton.addActionListener(e -> onStart());
        pauseButton.addActionListener(e -> onPauseResume());
        resetButton.addActionListener(e -> onReset());

        // initial
        applyTheme("Purple Blue");
        generateArray(arraySizeSlider.getValue());
    }

    private JLabel mkLbl(String s){
        JLabel l = new JLabel(s);
        l.setForeground(Color.WHITE);
        l.setFont(new Font("SansSerif", Font.BOLD, 14));
        return l;
    }

    private void applyTheme(String name){
    switch (name){
        case "Pink Orange":
            gradStart = new Color(255, 110, 180); // pink
            gradEnd   = new Color(255, 190, 120); // orange
            barColor  = Color.WHITE;
            hlColor   = new Color(255,255,255,200);
            break;
        case "Green Yellow":
            gradStart = new Color(40, 180, 120);  // green
            gradEnd   = new Color(220, 255, 160); // yellow
            barColor  = new Color(250,250,250);
            hlColor   = new Color(255,255,255,200);
            break;
        default: // Sky Blue → Dark Blue + Peach bars
            gradStart = new Color(135, 206, 250); // Sky Blue
            gradEnd   = new Color(0, 0, 139);     // Dark Blue
            barColor  = new Color(255, 218, 185); // Peach
            hlColor   = new Color(255, 235, 205, 180); // Highlight soft peach
    }
    centerPanel.repaint();
}

    private void generateArray(int size){
        array = new int[size];
        Random r = new Random();
        for(int i=0;i<size;i++) array[i] = r.nextInt(400) + 40;
        hiA = hiB = -1;
        centerPanel.repaint();
        status("Ready");
    }

    private void onStart(){
        if (isSorting) return;

        String algo = (String) algorithmDropdown.getSelectedItem();
        if (!"Bubble Sort".equals(algo)) {
            JOptionPane.showMessageDialog(this,
                    "Filhaal Bubble Sort animated hai. Dropdown me Bubble Sort select karke Start dabao.",
                    "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        isSorting = true;
        isPaused = false;
        pauseButton.setText("Pause");
        status("Running...");

        disableWhileSorting(true);

        sortThread = new Thread(this::bubbleSort);
        sortThread.start();
    }

    private void onPauseResume(){
        if (!isSorting) return;
        if (!isPaused){
            isPaused = true;
            pauseButton.setText("Resume");
            status("Paused");
        } else {
            synchronized (pauseLock){
                isPaused = false;
                pauseLock.notifyAll();
            }
            pauseButton.setText("Pause");
            status("Running...");
        }
    }

    private void onReset(){
        if (sortThread != null && sortThread.isAlive()){
            isSorting = false;
            synchronized (pauseLock){ isPaused = false; pauseLock.notifyAll(); }
            sortThread.interrupt();
        }
        disableWhileSorting(false);
        generateArray(arraySizeSlider.getValue());
        status("Ready");
        pauseButton.setText("Pause");
    }

    private void disableWhileSorting(boolean b){
        algorithmDropdown.setEnabled(!b);
        arraySizeSlider.setEnabled(!b);
        themeDropdown.setEnabled(!b);
        startButton.setEnabled(!b);
    }

    private void status(String s){
        SwingUtilities.invokeLater(() -> statusLabel.setText("Status: " + s));
    }

    private void stepDelay() {
        int speed = speedSlider.getValue();             // 1..100
        int delay = Math.max(1, 101 - speed) * 4;       // tune factor
        try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
    }

    private void pausePoint(){
        synchronized (pauseLock){
            while(isPaused){
                try { pauseLock.wait(); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void bubbleSort(){
        try{
            for (int i = 0; i < array.length - 1 && isSorting; i++){
                for (int j = 0; j < array.length - i - 1 && isSorting; j++){
                    hiA = j; hiB = j+1;
                    pausePoint();
                    if (array[j] > array[j+1]){
                        int t = array[j];
                        array[j] = array[j+1];
                        array[j+1] = t;
                    }
                    centerPanel.repaint();
                    stepDelay();
                }
            }
            hiA = hiB = -1;
            centerPanel.repaint();
            status("Completed");
        } finally {
            isSorting = false;
            disableWhileSorting(false);
        }
    }

    private class DrawPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g){
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();

            GradientPaint gp = new GradientPaint(
                    0, 0, gradStart,
                    getWidth(), getHeight(), gradEnd
            );
            g2.setPaint(gp);
            g2.fillRect(0, 0, getWidth(), getHeight());

            if (array != null && array.length > 0){
                int w = Math.max(1, getWidth() / array.length);
                for (int i=0;i<array.length;i++){
                    int x = i * w;
                    int h = array[i];
                    int y = getHeight() - h;

                    if (i == hiA || i == hiB) g2.setColor(hlColor);
                    else g2.setColor(barColor);

                    g2.fillRect(x, y, w - 2, h);
                }
            }
            g2.dispose();
        }
    }

    private String getComplexity(String algo){
        switch (algo){
            case "Bubble Sort":
                return "Best: O(n)\nAverage: O(n^2)\nWorst: O(n^2)\nSpace: O(1)";
            case "Selection Sort":
                return "Best: O(n^2)\nAverage: O(n^2)\nWorst: O(n^2)\nSpace: O(1)";
            case "Insertion Sort":
                return "Best: O(n)\nAverage: O(n^2)\nWorst: O(n^2)\nSpace: O(1)";
            case "Merge Sort":
                return "Best: O(n log n)\nAverage: O(n log n)\nWorst: O(n log n)\nSpace: O(n)";
            case "Quick Sort":
                return "Best: O(n log n)\nAverage: O(n log n)\nWorst: O(n^2)\nSpace: O(log n)";
            default:
                return "Select an algorithm...";
        }
    }

    public static void main(String[] args){
        SwingUtilities.invokeLater(() -> new SortingVisualizerUI().setVisible(true));
    }
}
