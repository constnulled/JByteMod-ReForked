package dev.joyel.ui;

import de.xbrowniecodez.jbytemod.Main;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

public final class ToastManager {

    public enum Kind {
        INFO(new Color(0x3B82F6), "i", 3500),
        SUCCESS(new Color(0x22C55E), "✓", 3000),
        WARNING(new Color(0xF59E0B), "!", 5000),
        ERROR(new Color(0xEF4444), "×", 7000);

        private final Color accent;
        private final String symbol;
        private final int duration;

        Kind(Color accent, String symbol, int duration) {
            this.accent = accent;
            this.symbol = symbol;
            this.duration = duration;
        }
    }

    private static final int TOAST_WIDTH = 340;
    private static final int TOAST_MIN_HEIGHT = 52;
    private static final int TOAST_MAX_HEIGHT = 160;
    private static final int MAX_TEXT_LENGTH = 240;
    private static final int MARGIN = 18;
    private static final int GAP = 10;
    private static final int MAX_VISIBLE = 5;
    private static final int FRAME_MS = 15;
    private static final int FADE_IN_MS = 180;
    private static final int FADE_OUT_MS = 260;

    private static final List<Toast> ACTIVE = new ArrayList<>();
    private static Window trackedOwner;

    private ToastManager() {
    }

    public static void info(String text) {
        show(text, Kind.INFO);
    }

    public static void success(String text) {
        show(text, Kind.SUCCESS);
    }

    public static void warning(String text) {
        show(text, Kind.WARNING);
    }

    public static void error(String text) {
        show(text, Kind.ERROR);
    }

    public static void show(String text, Kind type) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            create(text, type);
        } else {
            SwingUtilities.invokeLater(() -> create(text, type));
        }
    }

    private static void create(String text, Kind type) {
        Window owner = Main.INSTANCE.getJByteMod();
        if (owner == null || !owner.isShowing()) {
            owner = null;
        }
        track(owner);

        while (ACTIVE.size() >= MAX_VISIBLE) {
            ACTIVE.get(0).closeNow();
        }

        Toast toast = new Toast(owner, text, type);
        ACTIVE.add(toast);
        relayout();
        toast.start();
    }

    private static void track(Window owner) {
        if (owner == null || owner == trackedOwner) {
            return;
        }
        trackedOwner = owner;
        owner.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                relayout();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                relayout();
            }
        });
    }

    private static Rectangle anchorBounds() {
        if (trackedOwner != null && trackedOwner.isShowing()) {
            return trackedOwner.getBounds();
        }
        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle screen = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return new Rectangle(screen.x + insets.left, screen.y + insets.top,
                screen.width - insets.left - insets.right,
                screen.height - insets.top - insets.bottom);
    }

    private static void relayout() {
        Rectangle area = anchorBounds();
        int y = area.y + area.height - MARGIN;
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            Toast toast = ACTIVE.get(i);
            Dimension size = toast.getSize();
            y -= size.height;
            toast.setLocation(area.x + area.width - size.width - MARGIN, y);
            y -= GAP;
        }
    }

    private static int countLines(String text, FontMetrics fm, int width) {
        int lines = 0;
        for (String paragraph : text.split("\n", -1)) {
            int lineWidth = 0;
            int paragraphLines = 1;
            for (String word : paragraph.split(" ", -1)) {
                int wordWidth = fm.stringWidth(word);
                int spaceWidth = fm.charWidth(' ');
                if (wordWidth > width) {
                    if (lineWidth > 0) {
                        paragraphLines++;
                    }
                    paragraphLines += wordWidth / width;
                    lineWidth = wordWidth % width;
                } else if (lineWidth == 0) {
                    lineWidth = wordWidth;
                } else if (lineWidth + spaceWidth + wordWidth <= width) {
                    lineWidth += spaceWidth + wordWidth;
                } else {
                    paragraphLines++;
                    lineWidth = wordWidth;
                }
            }
            lines += paragraphLines;
        }
        return Math.max(1, lines);
    }

    private static final class Toast extends JWindow {
        private final Kind kind;
        private final Timer animator;
        private long phaseStart;
        private int phase;
        private boolean hovered;
        private long hoverStart;
        private boolean closed;

        Toast(Window owner, String text, Kind type) {
            super(owner);
            this.kind = type;
            setFocusableWindowState(false);
            setAlwaysOnTop(owner == null);
            setBackground(new Color(0x2B2D31));

            JPanel body = new JPanel(new BorderLayout(12, 0)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(0x2B2D31));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setColor(Toast.this.kind.accent);
                    g2.fillRect(0, 0, 6, getHeight());
                    g2.setColor(new Color(255, 255, 255, 28));
                    g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                    g2.dispose();
                }
            };
            body.setOpaque(true);
            body.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 14));

            JLabel icon = new JLabel(type.symbol, SwingConstants.CENTER) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Toast.this.kind.accent);
                    g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            icon.setForeground(Color.WHITE);
            icon.setFont(icon.getFont().deriveFont(Font.BOLD, 14f));
            icon.setPreferredSize(new Dimension(24, 24));
            JPanel iconWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            iconWrap.setOpaque(false);
            iconWrap.add(icon);

            int textWidth = TOAST_WIDTH - 18 - 14 - 24 - 12 - 4;
            String shown = text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) + "..." : text;
            JTextArea message = new JTextArea(shown);
            message.setLineWrap(true);
            message.setWrapStyleWord(true);
            message.setEditable(false);
            message.setFocusable(false);
            message.setOpaque(false);
            message.setBorder(null);
            message.setForeground(new Color(0xE6E6E6));
            message.setFont(message.getFont().deriveFont(Font.PLAIN, 13f));
            FontMetrics fm = message.getFontMetrics(message.getFont());
            int lines = countLines(shown, fm, (int) (textWidth * 0.88));
            int textHeight = Math.min(lines * fm.getHeight() + 4, TOAST_MAX_HEIGHT - 30);
            message.setPreferredSize(new Dimension(textWidth, textHeight));
            message.setMinimumSize(new Dimension(textWidth, textHeight));
            message.setMaximumSize(new Dimension(textWidth, textHeight));

            body.add(iconWrap, BorderLayout.WEST);
            JPanel textWrap = new JPanel(new BorderLayout());
            textWrap.setOpaque(false);
            textWrap.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
            textWrap.add(message, BorderLayout.CENTER);
            body.add(textWrap, BorderLayout.CENTER);
            setContentPane(body);
            pack();
            setSize(TOAST_WIDTH, Math.min(TOAST_MAX_HEIGHT, Math.max(getHeight(), TOAST_MIN_HEIGHT)));
            try {
                if (GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                        .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT)) {
                    setShape(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
                }
            } catch (UnsupportedOperationException | IllegalComponentStateException ignored) {
            }

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    hoverStart = System.currentTimeMillis();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (hovered) {
                        phaseStart += System.currentTimeMillis() - hoverStart;
                    }
                    hovered = false;
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    beginFadeOut();
                }
            };
            body.addMouseListener(mouse);
            message.addMouseListener(mouse);
            textWrap.addMouseListener(mouse);
            icon.addMouseListener(mouse);
            iconWrap.addMouseListener(mouse);

            animator = new Timer(FRAME_MS, e -> tick());
            animator.setCoalesce(true);
        }

        void start() {
            phase = 0;
            phaseStart = System.currentTimeMillis();
            setOpacityValue(0f);
            setVisible(true);
            animator.start();
        }

        private void tick() {
            long now = System.currentTimeMillis();
            if (phase == 0) {
                float t = Math.min(1f, (now - phaseStart) / (float) FADE_IN_MS);
                setOpacityValue(t);
                if (t >= 1f) {
                    phase = 1;
                    phaseStart = now;
                }
            } else if (phase == 1) {
                if (!hovered && now - phaseStart >= kind.duration) {
                    beginFadeOut();
                }
            } else if (phase == 2) {
                float t = Math.min(1f, (now - phaseStart) / (float) FADE_OUT_MS);
                setOpacityValue(1f - t);
                if (t >= 1f) {
                    closeNow();
                }
            }
        }

        private void beginFadeOut() {
            if (phase < 2) {
                phase = 2;
                phaseStart = System.currentTimeMillis();
                hovered = false;
            }
        }

        private void setOpacityValue(float value) {
            try {
                setOpacity(Math.max(0f, Math.min(1f, value)));
            } catch (UnsupportedOperationException | IllegalComponentStateException ignored) {
            }
        }

        void closeNow() {
            if (closed) {
                return;
            }
            closed = true;
            animator.stop();
            ACTIVE.remove(this);
            dispose();
            relayout();
        }
    }
}
