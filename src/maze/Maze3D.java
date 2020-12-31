//
// Maze£D.java
// Implements the 3D view dialog using JOGL (Jave OpenGL).
//
package maze;

import java.awt.DisplayMode;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureData;
import com.jogamp.opengl.util.texture.TextureIO;
import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import maze.Maze.Walls;

//
// Main 3D maze viewer using )-enGL
public class Maze3D implements GLEventListener {

    public static DisplayMode dm, dm_old;
    private final GLU glu = new GLU();

    // Animation properties (amount to move each frame and the total amount
    //   we want to move in x and z direction an camera rotation)
    private float x_move = 0.0f;
    private float z_move = 0.0f;
    private float movex = 0.0f;
    private float movez = 0.0f;
    private float target = 0.0f;
    private float targetx = 0.0f;
    private float targetz = 0.0f;
    private float turn_angle = 0.0f;
    private float turn_inc = 0.0f;

    // Current position of view
    private float x_pos = 0.0f;
    private float z_pos = 0.0f;

    private float angle = 90.0f;  // angle of view

    private final float move_frames = 10.0f;  // Number of frames to animate a move over
    private Walls direction;

    // Whether in orthogonal (final view)
    private boolean bOrtho = false;
    private float OrthoPC = 0.0f;

    // Helper class to pre-calculate and store co-ordinates for a circular maze
    private CircMazeCalc circ_pts[][];

    private Maze maze;             // The maze 
    private FPSAnimator animator;  // The animator

    private boolean solution = false;
    static private boolean displayed = false;

    final static int SEGMENTS = 5;  // Number of segemnts to draw a curved maze wll 
    final static float DEGTORAD = (2 * (float) Math.PI) / 360.0f;

    // Helper class to pre-calculate details of a circular maze
    public class CircMazeCalc {

        // Points on the inner curved wall of a cell
        ArrayList<Point2D> segments_inner;
        //Points on the outer curved wall of a cell
        ArrayList<Point2D> segments_outer;

        // Constructor, create the arrays and calculate the points of a cell
        CircMazeCalc(int x_pos, int width, int radius) {
            segments_inner = new ArrayList<>();
            segments_outer = new ArrayList<>();

            // Get the start angle
            float angle = (float) (Math.PI * 2 * x_pos) / width;

            // each wall has segments + 1 points, incrementing the angle
            for (int i = 0; i <= SEGMENTS; i++) {
                segments_outer.add(new Point2D.Float(
                        (float) (Math.cos(angle) * radius),
                        (float) (-Math.sin(angle) * radius)));
                segments_inner.add(new Point2D.Float(
                        (float) (Math.cos(angle) * (radius - 1)),
                        (float) (-Math.sin(angle) * (radius - 1))));

                angle += (float) (Math.PI * 2) / (width * SEGMENTS);
            }
        }

        // Get an inner wall point
        public Point2D getInner(int id) {
            return segments_inner.get(id);
        }

        // Get an Outer wall point
        public Point2D getOuter(int id) {
            return segments_outer.get(id);
        }
    }

    //  Main display code foe the maze
    @Override
    public void display(GLAutoDrawable drawable) {

        // Initialise the screen and viewpoint
        final GL2 gl = drawable.getGL().getGL2();
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
        gl.glLoadIdentity();

        // Get maze details
        int width = maze.getWidth();
        int height = maze.getHeight();

        // If animating move the viewpoint as needed
        if (animator.isAnimating()) {
            DoAnimate();
        }

        // If Othogonal (final) view calculate the viewpoint
        if (bOrtho) {
            // Set the voew angle
            gl.glRotatef(30.0f * OrthoPC, 1.0f, 0.5f, 0.0f);
            gl.glRotatef((angle + turn_angle) * (1.0f - OrthoPC), 0.0f, 1.0f, 0.0f);

            // Set the view position
            float x = maze.isCircular() ? 5.0f : -(float) width / 2.0f;
            float y = -7.5f;
            float z = (-8.0f - height);

            gl.glTranslatef(x * OrthoPC, y * OrthoPC, z * OrthoPC);
            gl.glTranslatef((x_pos + x_move) * (1.0f - OrthoPC),
                    -0.5f * (1.0f - OrthoPC),
                    (z_pos + z_move) * (1.0f - OrthoPC));
        } else {
            // Set the in maze view and position
            gl.glRotatef(angle + turn_angle, 0.0f, 1.0f, 0.0f);
            gl.glTranslatef(x_pos + x_move, -0.5f, z_pos + z_move);
        }

        gl.glBegin(GL2.GL_QUADS); // Start Drawing The quads
        gl.glColor3f(1f, 1f, 1f);

        // call the function to render the maze type
        if (!maze.isCircular()) {
            Draw3DRectangular(gl, width, height);
        } else {
            Draw3DCircular(gl, width, height);
        }

        gl.glEnd(); // Done Drawing The Quads, output it
        gl.glFlush();
    }

    // Function to draw the rectangular maze
    //
    public void Draw3DRectangular(GL2 gl, int width, int height) {

        // Start by drawing the floor tiles, for each cell
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {

                // Draw a Floor tile
                //  drawn as a textured quad, so need to define 4 texture co-ordinates
                //   and 4 spacial co-ordinates
                gl.glTexCoord2f(0.5f, 0.0f);
                gl.glVertex3f(i, 0.0f, j);
                gl.glTexCoord2f(1.0f, 0.0f);
                gl.glVertex3f(i, 0.0f, j - 1);
                gl.glTexCoord2f(1.0f, 1.0f);
                gl.glVertex3f(i + 1, 0.0f, j - 1);
                gl.glTexCoord2f(0.5f, 1.0f);
                gl.glVertex3f(i + 1, 0.0f, j);

                // If there is a West wall draw it (as a textured quad)
                if (maze.IsWall(i, j, Walls.West)) {
                    gl.glTexCoord2f(0.0f, 0.0f);
                    gl.glVertex3f(i, 0.0f, j);
                    gl.glTexCoord2f(0.5f, 0.0f);
                    gl.glVertex3f(i, 0.0f, j - 1);
                    gl.glTexCoord2f(0.5f, 1.0f);
                    gl.glVertex3f(i, 1.0f, j - 1);
                    gl.glTexCoord2f(0.0f, 1.0f);
                    gl.glVertex3f(i, 1.0f, j);
                }

                // If the East most cell need to draw an East wall (as a textured quad)
                if (i == width - 1) {
                    if (maze.IsWall(i, j, Walls.East)) {
                        gl.glTexCoord2f(0.0f, 0.0f);
                        gl.glVertex3f(i + 1, 0.0f, j);
                        gl.glTexCoord2f(0.5f, 0.0f);
                        gl.glVertex3f(i + 1, 0.0f, j - 1);
                        gl.glTexCoord2f(0.5f, 1.0f);
                        gl.glVertex3f(i + 1, 1.0f, j - 1);
                        gl.glTexCoord2f(0.0f, 1.0f);
                        gl.glVertex3f(i + 1, 1.0f, j);
                    }
                }

                // Draw the South wall (as a textured quad)
                if (maze.IsWall(i, j, Walls.South)) {
                    gl.glTexCoord2f(0.0f, 0.0f);
                    gl.glVertex3f(i, 0.0f, j);
                    gl.glTexCoord2f(0.5f, 0.0f);
                    gl.glVertex3f(i + 1, 0.0f, j);
                    gl.glTexCoord2f(0.5f, 1.0f);
                    gl.glVertex3f(i + 1, 1.0f, j);
                    gl.glTexCoord2f(0.0f, 1.0f);
                    gl.glVertex3f(i, 1.0f, j);
                }

                // If drawing the northmost cell, draw the north wall as well
                //  again a textured quad
                if (j == 0) {
                    if (maze.IsWall(i, j, Walls.North)) {
                        gl.glTexCoord2f(0.0f, 0.0f);
                        gl.glVertex3f(i, 0.0f, j - 1);
                        gl.glTexCoord2f(0.5f, 0.0f);
                        gl.glVertex3f(i + 1, 0.0f, j - 1);
                        gl.glTexCoord2f(0.5f, 1.0f);
                        gl.glVertex3f(i + 1, 1.0f, j - 1);
                        gl.glTexCoord2f(0.0f, 1.0f);
                        gl.glVertex3f(i, 1.0f, j - 1);
                    }
                }
            }
        }

        // If the user wants the solution ("Show me the way to go home" is checked
        if (solution) {
            // Get the solution iterator
            Iterator<Point> iter = maze.iterator3D();
            if (!iter.hasNext()) {
                return;
            }

            // Solution line is green
            gl.glColor3f(0f, 1f, 0f);

            // The maze iterator is set to iterate teh solution line from entrance to exit
            Point p1 = iter.next();
            Point p2;

            while (iter.hasNext()) {
                p2 = iter.next();

                // Calculate and draw as an untextured quad, so only the vertices 
                //   (spacial co-rdinates are neeeded)
                float minx = Math.min(p1.x, p2.x) + 0.45f;
                float maxx = Math.max(p1.x, p2.x) + 0.55f;
                float miny = Math.min(p1.y, p2.y) - 0.55f;
                float maxy = Math.max(p1.y, p2.y) - 0.45f;

                gl.glVertex3f(maxx, 0.1f, maxy);
                gl.glVertex3f(minx, 0.1f, maxy);
                gl.glVertex3f(minx, 0.1f, miny);
                gl.glVertex3f(maxx, 0.1f, miny);

                p1 = p2;
            }
        }
    }

    // Function to draw the circular maze
    //
    public void Draw3DCircular(GL2 gl, int width, int height) {

        // Draw the floor of the maze (currently drawn as a rectangle where the side
        //  width is equal to the diameter of the maze)
        float base_width = (float) (height + 3);
        for (int i = -(int) base_width; i < (int) base_width; i++) {
            for (int j = -(int) base_width; j < (int) base_width; j++) {
                // Draw a Floor tile
                //  drawn as a textured quad, so need to define 4 texture co-ordinates
                //   and 4 spacial co-ordinates
                gl.glTexCoord2f(0.5f, 0.0f);
                gl.glVertex3f(i, 0.0f, j);
                gl.glTexCoord2f(1.0f, 0.0f);
                gl.glVertex3f(i, 0.0f, j + 1);
                gl.glTexCoord2f(1.0f, 1.0f);
                gl.glVertex3f(i + 1, 0.0f, j + 1);
                gl.glTexCoord2f(0.5f, 1.0f);
                gl.glVertex3f(i + 1, 0.0f, j);
            }
        }

        // get the width of a texture segment. the texture bitmap contains
        //  2 images, 1/2 the with is the the floor segment, divided by the
        //  number of segments
        float texture_seg = 0.5f / SEGMENTS;

        // For each cell
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                // Draw the west wall
                if (maze.IsWall(i, j, Walls.West)) {
                    // get the pre-calculated start ond end points
                    float startX = (float) circ_pts[i][j].getInner(0).getX();
                    float startY = (float) circ_pts[i][j].getInner(0).getY();
                    float endX = (float) circ_pts[i][j].getOuter(0).getX();
                    float endY = (float) circ_pts[i][j].getOuter(0).getY();

                    // Draw as a textured quad
                    gl.glTexCoord2f(0.0f, 0.0f);
                    gl.glVertex3f(startX, 0.0f, startY);
                    gl.glTexCoord2f(0.5f, 0.0f);
                    gl.glVertex3f(endX, 0.0f, endY);
                    gl.glTexCoord2f(0.5f, 1.0f);
                    gl.glVertex3f(endX, 1.0f, endY);
                    gl.glTexCoord2f(0.0f, 1.0f);
                    gl.glVertex3f(startX, 1.0f, startY);
                }

                // Draw the north wall (which is curved), so draw as a set of textured quads
                if (maze.IsWall(i, j, Walls.North)) {
                    float startX = (float) circ_pts[i][j].getOuter(0).getX();
                    float startY = (float) circ_pts[i][j].getOuter(0).getY();
                    float texture = 0.0f;

                    // for each segment in the set
                    for (int s = 0; s < SEGMENTS; s++) {
                        float endX = (float) circ_pts[i][j].getOuter(s + 1).getX();
                        float endY = (float) circ_pts[i][j].getOuter(s + 1).getY();

                        gl.glTexCoord2f(texture, 0.0f);
                        gl.glVertex3f(startX, 0.0f, startY);
                        gl.glTexCoord2f(texture_seg + texture, 0.0f);
                        gl.glVertex3f(endX, 0.0f, endY);
                        gl.glTexCoord2f(texture_seg + texture, 1.0f);
                        gl.glVertex3f(endX, 1.0f, endY);
                        gl.glTexCoord2f(texture, 1.0f);
                        gl.glVertex3f(startX, 1.0f, startY);

                        // The enpoint is the start point of the next segment
                        startX = endX;
                        startY = endY;
                        texture += texture_seg;
                    }
                }

                // If we are at the innermost cells, Draw the south wall 
                //   (which is curved), so draw as a set of textured quads
                if (j == (height - 1)) {
                    if (maze.IsWall(i, j, Walls.South)) {
                        float startX = (float) circ_pts[i][j].getInner(0).getX();
                        float startY = (float) circ_pts[i][j].getInner(0).getY();
                        float texture = 0.0f;

                        // For each segment
                        for (int s = 0; s < SEGMENTS; s++) {
                            float endX = (float) circ_pts[i][j].getInner(s + 1).getX();
                            float endY = (float) circ_pts[i][j].getInner(s + 1).getY();

                            // Draw as a textured quad
                            gl.glTexCoord2f(texture, 0.0f);
                            gl.glVertex3f(startX, 0.0f, startY);
                            gl.glTexCoord2f(texture_seg + texture, 0.0f);
                            gl.glVertex3f(endX, 0.0f, endY);
                            gl.glTexCoord2f(texture_seg + texture, 1.0f);
                            gl.glVertex3f(endX, 1.0f, endY);
                            gl.glTexCoord2f(texture, 1.0f);
                            gl.glVertex3f(startX, 1.0f, startY);

                            // The end point becomes the start point of the next segemnt
                            startX = endX;
                            startY = endY;
                            texture += texture_seg;
                        }
                    }
                }

            }
        }

        // If the user wants a solution line ("Show me the way to go home" is checked)
        if (solution) {
            // get the solution iterator
            Iterator<Point> iter = maze.iterator3D();
            if (!iter.hasNext()) {
                return;
            }

            // Solution line is green
            gl.glColor3f(0f, 1f, 0f);

            // The maze iterator is set to iterate teh solution line 
            //  from currnt location to exit
            Point p1 = iter.next();
            Point p2;

            // Calculate the start point of the solution line segment
            float sect_angle = (float) (Math.PI * 2) / width;
            float angle1 = p1.x * sect_angle + sect_angle / 2;
            float x1 = (float) (Math.cos(angle1) * (height + 2.5f - p1.y));
            float y1 = (float) (-Math.sin(angle1) * (height + 2.5f - p1.y));

            //  while more solution lline parts
            while (iter.hasNext()) {
                p2 = iter.next();

                // Is it a straight of curved line.  A straight line is drawn as
                //   single object.  Curved line is segmented
                int segments = (p1.x == p2.x) ? 1 : SEGMENTS;
                for (int s = 1; s <= segments; s++) {
                    // Handle wrap around
                    int p2diff = p2.x - p1.x;
                    if (p2diff > 1) {
                        p2diff = -1;
                    }
                    if (p2diff < -1) {
                        p2diff = 1;
                    }

                    // Caluculate the end point
                    float end_seg = p1.x + ((float) p2diff * s) / segments;
                    float angle2 = end_seg * sect_angle + sect_angle / 2;

                    float x2 = (float) (Math.cos(angle2) * (height + 2.5f - p2.y));
                    float y2 = (float) (-Math.sin(angle2) * (height + 2.5f - p2.y));

                    // This stops the colution line from becoming too thin or disappearring
                    //  by ensuring the width values (+/- 0.05) do not go in the same direction
                    //  as the line.
                    float sgn = -1.0f;
                    if ((x1 - x2) != 0 && (y1 - y2) != 0) {
                        sgn = -Math.signum(x1 - x2) * Math.signum(y1 - y2);
                    }

                    // Draw the line segment
                    gl.glVertex3f(x1 + 0.05f, 0.1f, y1 + 0.05f * sgn);
                    gl.glVertex3f(x1 - 0.05f, 0.1f, y1 - 0.05f * sgn);
                    gl.glVertex3f(x2 - 0.05f, 0.1f, y2 - 0.05f * sgn);
                    gl.glVertex3f(x2 + 0.05f, 0.1f, y2 + 0.05f * sgn);

                    // end point becomes the start point of the next segment
                    x1 = x2;
                    y1 = y2;

                }
                p1 = p2;
            }
        }
    }

    // Handle disposal of the canvas
    @Override
    public void dispose(GLAutoDrawable drawable) {
        // clear the displayed flag, so that another 3D window can be generated
        displayed = false;
    }

    // Initialise the canvas
    @Override
    public void init(GLAutoDrawable drawable) {

        // Set the viewpoint and drawing parameters
        final GL2 gl = drawable.getGL().getGL2();
        gl.glShadeModel(GL2.GL_SMOOTH);
        gl.glClearColor(0f, 0f, 0f, 0f);
        gl.glClearDepth(1.0f);
        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glDepthFunc(GL2.GL_LEQUAL);
        gl.glHint(GL2.GL_PERSPECTIVE_CORRECTION_HINT, GL2.GL_NICEST);

        //Currently 2 textures wall and floor
        IntBuffer textures = IntBuffer.allocate(2);
        gl.glGenTextures(2, textures);

        // Load the texture bitmap (it is compiled into the resources, so it part 
        //  of the jar file, rather than a separate file
        try {
            InputStream stream = getClass().getResourceAsStream("/textures/brick.jpg");
            final TextureData data = TextureIO.newTextureData(gl.getGLProfile(), stream, false, "jpg");
            Texture brickTexture = TextureIO.newTexture(data);
            brickTexture.enable(gl);
            brickTexture.bind(gl);
        } catch (IOException exc) {
            System.exit(1);
        }

        // Enable texturing
        gl.glEnable(GL2.GL_TEXTURE_2D);

        // If maze of circular
        if (maze.isCircular()) {
            // camera starts at centre. logically (0,0)
            x_pos = 0.0f;
            z_pos = 0.0f;

            // Get maze details
            int width = maze.getWidth();
            int height = maze.getHeight();

            // Create and pre-calculate points
            circ_pts = new CircMazeCalc[width][height];

            for (int i = 0; i < width; i++) {
                int radius = height + 3;
                for (int j = 0; j < height; j++) {
                    circ_pts[i][j] = new CircMazeCalc(i, width, radius);
                    radius--;
                }
            }

            // Calculate the initial camera angle for a circular maze
            angle = 90.0f - (((float) maze.getEntrancePos() + 0.5f) * 360.0f / maze.getWidth());
            if (angle < 0.0f) {
                angle += 360.0f;
            }
        } else {
            // Set the view angle and position for a rectangular maze 
            angle = 0.0f;
            x_pos = -maze.getEntrancePos() - 0.5f;
            z_pos = -maze.getHeight();
        }

        direction = Walls.North;
        maze.setPos(maze.getEntrancePos(), maze.getHeight());
    }

    //  Set the new view details if window is resized
    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {

        // TODO Auto-generated method stub
        final GL2 gl = drawable.getGL().getGL2();
        if (height <= 0) {
            height = 1;
        }

        // Set the new viewpoints
        final float h = (float) width / (float) height;
        gl.glViewport(0, 0, width, height);
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();

        glu.gluPerspective(45.0f, h, 0.1f, 40.0f);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glLoadIdentity();
    }

    // Called when a animation completes to reset all the animation details to 0
    public void StopAnimation() {
        x_move = 0.0f;
        movex = 0.0f;
        z_move = 0.0f;
        movez = 0.0f;
        turn_inc = 0.0f;
        turn_angle = 0.0f;
        targetx = 0.0f;
        targetz = 0.0f;
//        bOrtho = false;
//        OrthoPC = 0.0f;
        animator.stop();  // Tell the animetor to stop calling the draw function
    }

    // Handle the animation
    public void DoAnimate() {
        // store the old points
        float oldx = x_move;
        float oldy = z_move;
        float old_angle = turn_angle;

        // Increment by the per-frame animation increments
        x_move += movex;
        z_move += movez;
        turn_angle += turn_inc;

        // If we have completed the maze
        if (bOrtho) {
            // Move the camera view until it reaches its final resting point
            if (OrthoPC < 1.0f) {
                OrthoPC += 0.01f;
            } else {
                // We've got there so ensure the animation is stopped and
                // display the final message
                StopAnimation();
                String msg = "<html><b>*** CONGRATULATIONS ***</b><BR>You have successfully solved the maze";
                JOptionPane.showMessageDialog(null, msg);
            }

            // Otherwise for general animation check is we have reached our end point
        } else if (Math.signum(turn_angle - target) != Math.signum(old_angle - target)
                || Math.signum(z_move - targetz) != Math.signum(oldy - targetz)
                || Math.signum(x_move - targetx) != Math.signum(oldx - targetx)) {

            // Set the final position an angle
            if (x_move != 0.0f) {
                x_pos += targetx;
            }
            if (z_move != 0.0f) {
                z_pos += targetz;
            }
            if (turn_inc != 0.0f) {
                angle = (angle + target + 360.0f) % 360.0f;
            }
            // Player has exited the maze via the entrance
            // turn them around to go back in
            if (maze.AtStart() && direction == Walls.South) {
                if (maze.isCircular()) {
                    target = -180.0f;
                    targetz = -z_pos;
                    targetx = -x_pos;
                } else {
                    target = -180.0f;
                    targetz = -1.0f;
                    targetx = 0.0f;
                }
                z_move = 0.0f;
                x_move = 0.0f;
                turn_inc = target / (move_frames * 4);
                movez = targetz / (move_frames * 4);
                movex = targetx / (move_frames * 4);
                direction = Walls.North;
            } else if (maze.Completed()) {  // If completed set orthogonal view mode
                StopAnimation();
                bOrtho = true;
                OrthoPC = 0.0f;
                if (angle > 180.0f) {
                    angle -= 360.f;
                }
                animator.start();
            } else {
                StopAnimation(); // otherwise stop the animation
            }
        }
    }

    // Calculate the movement from the current position to go to a location
    public void CircularPosition() {
        Point pt = maze.getLocation();

        float radius = (float) (maze.getHeight() - pt.y + 2);
        float segment_width = 360.0f / maze.getWidth();
        float end_angle = 90.0f - (((float) pt.x + 0.5f) * segment_width);
        float face = end_angle;

        // Set the adjustment to put the viepoint on the back line of the cell,
        // rather than the centre ( the back-line depends of the direction for 
        //  example if facing East, the back line is the West-most edge of the cell)
        switch (direction) {
            case North:
                break;
            case East:
                radius += 0.5f;
                end_angle -= segment_width / 2;
                face += 90.0f;
                break;
            case South:
                radius += 1.0f;
                face += 180.0f;
                break;
            case West:
                radius += 0.5f;
                end_angle += segment_width / 2;
                face -= 90.0f;
                break;

            default:
                System.out.println("Invalid direction");
                return;
        }
        float endx = radius * (float) -Math.cos((90.0f - end_angle) * DEGTORAD);
        float endz = radius * (float) Math.sin((90.0f - end_angle) * DEGTORAD);

        // How far do we have to move to get to the targer
        targetx = endx - x_pos;
        targetz = endz - z_pos;

        // calculate and normalise the angle
        target = face - angle;
        while (target > 180.0f) {
            target -= 360.0f;
        }
        while (target < -180.0f) {
            target += 360.0f;
        }

        // Set the increments to get there over a number of frames
        movex = targetx / move_frames;
        movez = targetz / move_frames;
        turn_inc = target / move_frames;
    }

    // Move forward a specified dispance in the direction we are facing
    public void forward(float distance) {
        targetx = distance * (float) -Math.cos((90.0f - angle) * DEGTORAD);
        targetz = distance * (float) Math.sin((90.0f - angle) * DEGTORAD);
        movex = targetx / move_frames;
        movez = targetz / move_frames;
    }

    // Handle the up button (forward button being pressed)
    public void GoForward() {
        // if moving, can't initiate another movement
        if (animator.isAnimating()) {
            return;
        }

        // If maze is circular calculate new position
        if (maze.isCircular()) {
            if (x_pos == 0.0f && z_pos == 0.0f) {
                maze.Move(Walls.North);
                //forward(3.0f);
                CircularPosition();

            } // Drawing view is 2D draw anti-clockwise and angle in OpenGl are clock-wise
            // so need to invert East and West
            else if (maze.Move(direction.InverseLR())) {
                //forward(1.0f);
                CircularPosition();
            }
        } else if (maze.Move(direction)) {
            forward(1.0f);
        }

        // If viewpoint moved, start the animation to move there
        if (targetx != 0.0f || targetz != 0.0f) {
            animator.start();
        }
    }

    // Handle user pressing the left cursor key
    public void TurnLeft() {
        // if moving, can't initiate another movement
        if (animator.isAnimating() || !maze.canTurn()) {
            return;
        }

        // Set the new turn angle
        target = -90.0f;
        turn_inc = target / move_frames;
        
        // calculate the new view position as it is on the back line of the cell
        //  ao changing the direction changes the view position
        if (maze.isCircular()) {
            direction = direction.TurnLeft();
            CircularPosition();
            animator.start();
            //    target = -360.0f / maze.getWidth();
            //    turn_inc = target / move_frames;
        } else {
            switch (direction) {
                case North:
                    targetx = -0.5f;
                    targetz = 0.5f;
                    break;
                case East:
                    targetx = -0.5f;
                    targetz = -0.5f;
                    break;
                case South:
                    targetx = 0.5f;
                    targetz = -0.5f;
                    break;
                case West:
                    targetx = 0.5f;
                    targetz = 0.5f;
                    break;

                default:
                    System.out.println("Invalid direction");
                    return;
            }

            movex = targetx / move_frames;
            movez = targetz / move_frames;
            direction = direction.TurnLeft();
            
            // Start the animation
            animator.start();
        }

    }

    // Handle user pressing the left cursor key
    public void TurnRight() {
        // if moving, can't initiate another movement
        if (animator.isAnimating() || !maze.canTurn()) {
            return;
        }

        // Set the new turn angle
        target = 90.0f;
        turn_inc = target / move_frames;
        
        // calculate the new view position as it is on the back line of the cell
        //  ao changing the direction changes the view position
        if (maze.isCircular()) {
            direction = direction.TurnRight();
            CircularPosition();
            animator.start();
            //    target = 360.0f / maze.getWidth();
            //    turn_inc = target / move_frames;
        } else {
            switch (direction) {
                case North:
                    targetx = 0.5f;
                    targetz = 0.5f;
                    break;
                case East:
                    targetx = -0.5f;
                    targetz = 0.5f;
                    break;
                case South:
                    targetx = -0.5f;
                    targetz = -0.5f;
                    break;
                case West:
                    targetx = 0.5f;
                    targetz = -0.5f;
                    break;

                default:
                    System.out.println("Invalid direction");
                    return;
            }

            movex = targetx / move_frames;
            movez = targetz / move_frames;
            direction = direction.TurnRight();
            
            // Start the animation
            animator.start();
        }

    }

    // Class for the frame handling the input
    public static class mainFrame extends JFrame implements KeyListener {

        @Override
        public void keyPressed(KeyEvent e) {
        }

        // Call the correct function on a cursor key
        @Override
        public void keyReleased(KeyEvent e) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_RIGHT:
                    assoc.TurnRight();
                    break;
                case KeyEvent.VK_LEFT:
                    assoc.TurnLeft();
                    break;
                case KeyEvent.VK_DOWN:
                    //draw.moveDown();
                    break;
                case KeyEvent.VK_UP:
                    assoc.GoForward();
                    break;
                default:
                    break;
            }
        }

        @Override
        public void keyTyped(KeyEvent e) {
        }

        // Set the frame parameters
        public mainFrame(String title, Maze3D maze) {
            super(title);
            assoc = maze;
            addKeyListener(this);
            setFocusable(true);
            setFocusTraversalKeysEnabled(false);
        }
        private final Maze3D assoc;
    }

    // Handle the 3D button being pressed
    public static void create(Maze mazeOb) {

        if (displayed) {
            JOptionPane.showMessageDialog(null, "Only one 3D view is allowed at a time");
            return;
        }

        // Create the OpenGL canvas
        final GLProfile profile = GLProfile.get(GLProfile.GL2);
        GLCapabilities capabilities = new GLCapabilities(profile);

        // The canvas
        final GLCanvas glcanvas = new GLCanvas(capabilities);
        
        // The frame to hold the canvas
        Maze3D MazeRenderer = new Maze3D();

        // Set displayed to true, so we cannot create two at a time
        displayed = true;

        // reset the solution
        MazeRenderer.maze = mazeOb;
        MazeRenderer.maze.ResetSolution();

        glcanvas.addGLEventListener(MazeRenderer);
        glcanvas.setSize(800, 600);

        final mainFrame frame = new mainFrame("3D Maze traversal", MazeRenderer);
        frame.getContentPane().add(glcanvas);

        // Add a check box
        JCheckBox show_solution = new JCheckBox("Show me the way to go home");
        frame.getContentPane().add(show_solution, BorderLayout.PAGE_END);
        show_solution.addActionListener((ActionEvent evt) -> {
            // When the checkbox is changed
            MazeRenderer.solution = show_solution.isSelected();
            // re-display the maze with/without the solution line
            glcanvas.display();
            
            // Give the focus back to the frame
            frame.requestFocus();
        });

        // Set the "X" button action to delete the view (default is to hide it)
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        
        // Display the Window
        frame.setSize(frame.getContentPane().getPreferredSize());
        frame.setVisible(true);
        frame.setLocationRelativeTo(null);
        
        // Create an animator
        MazeRenderer.animator = new FPSAnimator(glcanvas, 300, true);
    }
}
