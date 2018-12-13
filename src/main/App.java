package main;

import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.FPSAnimator;

import javax.swing.*;
import java.awt.*;

public class App {

    private App() {
        try {
            GLProfile profile = GLProfile.getMaximum(true);
            GLCapabilities capabilities = new GLCapabilities(profile);

            GLCanvas canvas = new GLCanvas(capabilities);
            canvas.addGLEventListener(new Renderer());
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
        SwingUtilities.invokeLater(App::new);
    }

}