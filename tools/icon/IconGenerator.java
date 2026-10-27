import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Draws the Panorama Kit app icon: a Material-style rounded square with a soft elevation shadow and
 * a flat panorama panel in the middle - a white frame split into panels by two slots, with blue
 * ground showing through, white hills and an amber sun.
 *
 * <p>Also writes the wider README banner (icon mark plus wordmark).
 *
 * <p>Run it with any JDK 17+:
 * {@code java tools/icon/IconGenerator.java <workspace-root>}
 */
public final class IconGenerator {

    /** Everything below is drawn in a 512x512 design space and scaled up for crisp edges. */
    private static final int DESIGN = 512;
    private static final int RENDER = 1024;

    private static final Color[] PLATE = {
        new Color(0x3D5AFE), // Material blue A400
        new Color(0x1A237E), // Material indigo 900
    };
    private static final Color ACCENT = new Color(0xFFC93C);
    private static final Color SHADOW = new Color(0x0A1020);
    private static final Color BACKGROUND = new Color(0x11151C);
    private static final Color TEXT = new Color(0xF3F6FA);
    private static final Color TEXT_DIM = new Color(0x93A0B4);
    private static final double PLATE_INSET = 44;

    /** The panorama panel: a wide frame split into three panels, like the mod's own preview pane. */
    private static final double SUN_X = 160;
    private static final double SUN_Y = 234;
    private static final double SUN_R = 25;

    private static final double PANEL_X = 76;
    private static final double PANEL_Y = 158;
    private static final double PANEL_W = 360;
    private static final double PANEL_H = 196;
    private static final double FRAME = 22;
    private static final double PANEL_RADIUS = 26;
    private static final double SLOT_W = 10;

    public static void main(String[] args) throws Exception {
        // Headless keeps Java2D from initialising the OS toolkit, which is slow and leaves threads
        // behind; text is still rendered normally.
        System.setProperty("java.awt.headless", "true");
        Path root = Path.of(args.length > 0 ? args[0] : ".");
        BufferedImage render = render();

        BufferedImage large = scale(render, 1024);
        BufferedImage icon = scale(large, 256); // what ships inside the mod jar

        Files.createDirectories(root.resolve("docs"));
        Files.createDirectories(root.resolve("src/main/resources"));
        write(icon, root.resolve("src/main/resources/panoramakit.png"));
        write(large, root.resolve("docs/icon-1024.png"));
        write(banner(render), root.resolve("docs/banner.png"));
        System.out.println("Wrote src/main/resources/panoramakit.png (256x256), docs/icon-1024.png, docs/banner.png");
        // Java2D can leave non-daemon AWT threads behind; this keeps the tool from hanging.
        System.exit(0);
    }

    private static BufferedImage render() {
        BufferedImage image = new BufferedImage(RENDER, RENDER, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(RENDER / (double) DESIGN, RENDER / (double) DESIGN);

        drawPlate(g);
        drawPanorama(g);
        g.dispose();
        return image;
    }

    /** The rounded "squircle" plate of the icon. */
    private static Shape plate() {
        double size = DESIGN - PLATE_INSET * 2;
        return new RoundRectangle2D.Double(PLATE_INSET, PLATE_INSET, size, size, size * 0.30, size * 0.30);
    }

    private static void drawPlate(Graphics2D g) {
        Shape plate = plate();
        drawElevation(g, plate, 34, 12, 0.30f);

        g.setPaint(new LinearGradientPaint(
                new Point2D.Double(0, 0),
                new Point2D.Double(DESIGN, DESIGN),
                new float[] { 0f, 1f },
                PLATE,
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(plate);

        Shape old = g.getClip();
        g.clip(plate);

        // Soft top-left highlight, the Material "surface tint".
        g.setPaint(new RadialGradientPaint(
                new Point2D.Double(DESIGN * 0.26, DESIGN * 0.20),
                (float) (DESIGN * 0.72),
                new float[] { 0f, 1f },
                new Color[] { new Color(255, 255, 255, 46), new Color(255, 255, 255, 0) },
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(plate);

        // Slightly darker bottom edge for depth.
        g.setPaint(new LinearGradientPaint(
                new Point2D.Double(0, DESIGN * 0.45),
                new Point2D.Double(0, DESIGN),
                new float[] { 0f, 1f },
                new Color[] { new Color(0, 0, 0, 0), new Color(0, 0, 0, 48) },
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(plate);

        g.setClip(old);
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(255, 255, 255, 38));
        g.draw(plate);
    }

    /**
     * The flat panorama panel: the white frame is split into three panels by two vertical slots, the
     * inside of the frame is the plate showing through, and the scene on it is drawn in white with an
     * amber sun.
     */
    private static void drawPanorama(Graphics2D g) {
        Shape panel = panel();
        Shape interior = interior();
        Area slots = slots();

        // White parts: the frame, minus the two slots that cut it into panels.
        Area frame = new Area(panel);
        frame.subtract(new Area(interior));
        frame.subtract(slots);

        // The scene never covers the frame or the slots, so the panels stay readable.
        Area scene = new Area(interior);
        scene.subtract(slots);

        drawElevation(g, frame, 18, 8, 0.30f);

        g.setColor(Color.WHITE);
        g.fill(frame);

        Shape old = g.getClip();
        g.clip(scene);

        // One mountain with its peak in the middle panel and a lower hill in the right panel, so
        // every panel of the panorama reads on its own.
        g.fill(hill(114, 316, 258, 232, 402, 316));
        g.fill(hill(286, 316, 352, 268, 412, 316));

        // Warm haze around the sun, drawn over the hill so it reads as light.
        g.setPaint(new RadialGradientPaint(
                new Point2D.Double(SUN_X, SUN_Y),
                64,
                new float[] { 0f, 1f },
                new Color[] { new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 105), new Color(255, 201, 60, 0) },
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(new Ellipse2D.Double(SUN_X - 64, SUN_Y - 64, 128, 128));

        g.setColor(ACCENT);
        g.fill(new Ellipse2D.Double(SUN_X - SUN_R, SUN_Y - SUN_R, SUN_R * 2, SUN_R * 2));

        g.setClip(old);
    }

    private static Shape panel() {
        return new RoundRectangle2D.Double(PANEL_X, PANEL_Y, PANEL_W, PANEL_H, PANEL_RADIUS, PANEL_RADIUS);
    }

    private static Shape interior() {
        return new RoundRectangle2D.Double(
                PANEL_X + FRAME,
                PANEL_Y + FRAME,
                PANEL_W - FRAME * 2,
                PANEL_H - FRAME * 2,
                PANEL_RADIUS - FRAME * 0.5,
                PANEL_RADIUS - FRAME * 0.5);
    }

    /** The two vertical slots that split the panel into three, the way a panorama wraps around. */
    private static Area slots() {
        Area slots = new Area();
        double third = PANEL_W / 3.0;
        for (int i = 1; i <= 2; i++) {
            double x = PANEL_X + third * i - SLOT_W / 2;
            slots.add(new Area(new Rectangle2D.Double(x, PANEL_Y, SLOT_W, PANEL_H)));
        }
        return slots;
    }

    private static Shape hill(double x0, double baseY, double peakX, double peakY, double x1, double baseY2) {
        GeneralPath path = new GeneralPath();
        path.moveTo(x0, baseY);
        path.lineTo(peakX, peakY);
        path.lineTo(x1, baseY2);
        path.closePath();
        return path;
    }

    /** The README banner: the icon mark on the left, the wordmark and tagline on the right. */
    private static BufferedImage banner(BufferedImage render) {
        int width = 1280;
        int height = 400;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        g.setColor(BACKGROUND);
        g.fillRect(0, 0, width, height);

        // A faint blue wash from the left, matching the icon plate.
        g.setPaint(new RadialGradientPaint(
                new Point2D.Double(150, height * 0.5),
                620,
                new float[] { 0f, 1f },
                new Color[] { new Color(0x2B3F9E), BACKGROUND },
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fillRect(0, 0, width, height);

        int mark = 232;
        g.drawImage(render, 72, (height - mark) / 2, mark, mark, null);

        int textX = 72 + mark + 52;
        drawText(g, "Panorama Kit", textX, 196, new Font(Font.SANS_SERIF, Font.BOLD, 68), TEXT);
        drawText(g, "Panorama screenshots and instant main-menu backgrounds for Minecraft", textX, 246,
                new Font(Font.SANS_SERIF, Font.PLAIN, 26), TEXT_DIM);
        drawText(g, "NeoForge  /  Minecraft 26.1 - 26.3  /  Java 25", textX, 290,
                new Font(Font.MONOSPACED, Font.PLAIN, 22), new Color(0x6F7D93));

        g.dispose();
        return image;
    }

    private static void drawText(Graphics2D g, String text, int x, int baseline, Font font, Color color) {
        FontRenderContext context = g.getFontRenderContext();
        GlyphVector glyphs = font.createGlyphVector(context, text);
        g.setColor(color);
        g.fill(glyphs.getOutline(x, baseline));
    }

    /** Draws a blurred copy of the shape below it, the Material way of showing elevation. */
    private static void drawElevation(Graphics2D g, Shape shape, int blur, double offsetY, float alpha) {
        BufferedImage mask = new BufferedImage(RENDER, RENDER, BufferedImage.TYPE_INT_ARGB);
        Graphics2D maskGraphics = mask.createGraphics();
        maskGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        maskGraphics.scale(RENDER / (double) DESIGN, RENDER / (double) DESIGN);
        maskGraphics.setColor(new Color(SHADOW.getRed(), SHADOW.getGreen(), SHADOW.getBlue(),
                Math.round(255 * alpha)));
        maskGraphics.fill(shape);
        maskGraphics.dispose();

        mask = blur(mask, blur);
        // The mask is already in device pixels, so draw it without the design-space scaling.
        AffineTransform transform = g.getTransform();
        g.setTransform(new AffineTransform());
        g.drawImage(mask, 0, (int) Math.round(offsetY * (RENDER / (double) DESIGN)), null);
        g.setTransform(transform);
    }

    /** Cheap repeated 3x3 box blur; good enough for an icon shadow. */
    private static BufferedImage blur(BufferedImage source, int passes) {
        Kernel kernel = new Kernel(3, 3, new float[] {
            1f / 9, 1f / 9, 1f / 9,
            1f / 9, 1f / 9, 1f / 9,
            1f / 9, 1f / 9, 1f / 9,
        });
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        BufferedImage result = source;
        for (int i = 0; i < passes; i++) {
            BufferedImage next = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
            op.filter(result, next);
            result = next;
        }
        return result;
    }

    private static BufferedImage scale(BufferedImage source, int size) {
        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(source, 0, 0, size, size, null);
        g.dispose();
        return target;
    }

    private static void write(BufferedImage image, Path path) throws Exception {
        ImageIO.write(image, "png", path.toFile());
    }

    private IconGenerator() {}
}
