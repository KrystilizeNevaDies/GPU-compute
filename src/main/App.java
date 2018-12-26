package main;

import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.FPSAnimator;

import javax.swing.*;
import java.awt.*;

import static main.App.Mode.MAX;
import static main.App.Mode.SORT;

public class App {

    enum Mode {
        MAX, SORT
    }

    private App(Mode mode) {
        try {
            GLProfile profile = GLProfile.getMaximum(true);
            GLCapabilities capabilities = new GLCapabilities(profile);

            GLCanvas canvas = new GLCanvas(capabilities);

            if (mode == MAX) canvas.addGLEventListener(new RendererMax());
            else canvas.addGLEventListener(new RendererSortBitonic());

            canvas.setSize(1, 1);

            Frame testFrame = new Frame("TestFrame");
            testFrame.add(canvas);
            testFrame.pack();
            testFrame.setVisible(true);

            new FPSAnimator(canvas, 60, true).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        final Mode mode;
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("sort")) mode = SORT;
            else mode = MAX;
        } else mode = MAX;

        SwingUtilities.invokeLater(() -> new App(mode));
    }

}