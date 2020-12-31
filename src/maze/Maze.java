//
// Maze.java
// Implements the class which generates and handles the maze.
//
//  This currently supports rectangular and circular mazes.  A circular maze
//    is essentially a a rectangular maze which has been stretched and wrapped,
//    ao that far east and far west walls are the same.
//
package maze;

import java.awt.Point;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Random;

//
// Main maze class
public class Maze implements Iterable<Point> {

    private int width;  // width and height of maze
    private int height;
    private int entrance;  // entrance and exit positions
    private int exit;
    private int xpos = 0;  // current position (when traversing 3D maze)
    private int ypos = 0;
    private EnumSet<Walls> mazemap[][];  // The map pf the maze
    private boolean bMapCreated;
    private boolean bMapCircular = false;  // is maze circular
    private final Random rand;
    private ArrayList<Point> solution = null;  // solution details as a array of map square co-ordinates
    private ArrayList<Point> solution3D = null;

    // Iterator for the full solution of the maze (actually goes exit to entrance)
    @Override
    public Iterator<Point> iterator() {
        return (solution.iterator());
    }

    // Iterator for the solution from the current position of the maze 
    //  (actually goes exit to entrance), used in the 3D view.
    public Iterator<Point> iterator3D() {
        return (solution3D.iterator());
    }

    // definition of a maze square, defining the walls  around the square
    public enum Walls {
        North,
        East,
        South,
        West,
        Available, // used for creating the map
        SolveAvail;     // used for solving map  

        public static final EnumSet<Walls> ALL = EnumSet.allOf(Walls.class);

        // Given a direction, returns the way you will be facing if you turn left
        public Walls TurnLeft() {
            return values()[(ordinal() + 3) % 4];
        }

        // Given a direction, returns the way you will be facing if you turn right
        public Walls TurnRight() {
            return values()[(ordinal() + 1) % 4];
        }

        // Given a direction, inverts the left and right
        //  Used when navigating a circular maze as the maze is drawn anti-clockwse
        //   as a right turn logically becomes a left turn and vice versa
        public Walls InverseLR() {

            return values()[(ordinal() + (ordinal() % 2) * 2) % 4];
        }
    };

    // Return whether a maze has been created
    public boolean isCreated() {
        return bMapCreated;
    }

    // determine os the player can turn when in £D maze
    public boolean canTurn() {
        return (ypos < height && ypos >= 0);
    }

    // move the current position in the specified direction
    public boolean Move(Walls wall) {
        int prevx = xpos;
        int prevy = ypos;

        // if at entrance can always move
        if (ypos == height && wall == Walls.North) {
            solution3D.remove(solution3D.size() - 1);
            ypos--;
            return true;
        } else if (ypos < 0) {  // exited maze, cannot move
            return false;
        } else if (mazemap[xpos][ypos].contains(wall)) { // bashed into a wall
            return (false);
        }
        
        // Determine direction, and change position
        switch (wall) {
            case North:
                ypos--;
                break;
            case South:
                ypos++;
                break;
            case East:
                xpos = (xpos + 1) % width;
                break;
            case West:
                xpos = (xpos + width - 1) % width;
                break;
        }
        
        // Remove the point from the solution, if the player moved onto the 
        //  correct point (closer to the exit) or add the point of the player
        //  went the wrong way (away from the exit)
        if (solution3D.isEmpty()){
            solution3D.add(new Point(prevx, prevy));
        }
        Point end = solution3D.get(solution3D.size() - 1);
        if (end.x == xpos && end.y == ypos) {
            solution3D.remove(solution3D.size() - 1);
        } else {
            solution3D.add(new Point(prevx, prevy));
        }
        return true;
    }

    // Set whether maze circular
    public void SetCircular(boolean bCirc) {
        bMapCircular = bCirc;
    }

    // set the maze size
    public void setSize(int w, int h) {
        width = w;
        height = h;
    }

    // Get the position of the entrance
    public int getEntrancePos() {
        return (entrance);
    }

    // Create a base map (alls walls in place).
    //  The creation works by having all walls sets, then creating a path
    //  by breaking the walls
    private void ClearMap() {
        mazemap = new EnumSet[width][height];

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                mazemap[i][j] = EnumSet.copyOf(Walls.ALL);
            }
        }
    }

    // Is a particular square available when creating a maze
    //  an available square is one which is not yet connected to the maze
    private boolean Available(int x, int y) {
        if (x < 0 || x >= width) {
            if (bMapCircular) {
                x = (x < 0) ? width - 1 : 0;
            } else {
                return (false);
            }
        }
        if (y < 0 || y >= height) {
            return (false);
        }

        return (mazemap[x][y].contains(Walls.Available));
    }

    // When creating the maze count the number of directions that are available
    //  to add the the maze (North, South, East, West)
    private int CountDirs(int x, int y) {
        int count = 0;

        if (Available(x - 1, y)) {
            count++;
        }
        if (Available(x, y - 1)) {
            count++;
        }
        if (Available(x + 1, y)) {
            count++;
        }
        if (Available(x, y + 1)) {
            count++;
        }

        return (count);
    }

    // When creating the maze get the nth available direction
    private Walls GetDirection(int x, int y, int item) {
        int count = -1;

        if (Available(x - 1, y)) {
            if (++count == item) {
                return (Walls.West);
            }
        }
        if (Available(x, y - 1)) {
            if (++count == item) {
                return (Walls.North);
            }
        }
        if (Available(x + 1, y)) {
            if (++count == item) {
                return (Walls.East);
            }
        }
        if (Available(x, y + 1)) {
            if (++count == item) {
                return (Walls.South);
            }
        }
        // Should not get here but if we do this will remove the square from the list
        return (Walls.Available);
    }

    // randomly select a direction to break the wall
    private Walls SelectWall(int x, int y) {
        // Get the number of directions we can go
        int count = CountDirs(x, y);

        // If we cannot move on the path, tell the caller
        if (count == 0) {
            return Walls.Available;
        }
        // Pick and return one of the available directions
        int item = rand.nextInt(count);

        return (GetDirection(x, y, item));
    }

    // break down a wall in the maze to create a path
    private Point BreakWall(int x, int y, Walls wall) {
        int x1 = x;
        int y1 = y;
        Walls Opposing = Walls.Available;

        // Determine the square we are moving to and the corresponding wall 0n that square
        switch (wall) {
            case North:
                y1--;
                Opposing = Walls.South;
                break;
            case South:
                y1++;
                Opposing = Walls.North;
                break;
            case East:
                x1++;
                Opposing = Walls.West;
                break;
            case West:
                x1--;
                Opposing = Walls.East;
                break;
        }

        // Remove the wall
        mazemap[x][y].remove(wall);

        // On a circular maze handle wrap around
        if (x1 < 0 || x1 >= width) {
            // Wrap around if circular
            if (bMapCircular) {
                x1 = (x1 < 0) ? width - 1 : 0;
            }
        }

        // Break down the opposite wall of the square we are moving to 
        //  so the path is clear both ways and mark the square as included
        //  in the maze, so no longer available to be added to the maze.
        if (x1 >= 0 && x1 < width && y1 >= 0 && y1 < height) {
            mazemap[x1][y1].remove(Opposing);
            mazemap[x1][y1].remove(Walls.Available);
        }
        
        // return the point we have just open up by breaking down the wall
        return (new Point(x1, y1));
    }

    // Determine if a move from a specified point in a partilulsr direction
    //  is possible, used in searching for a solution
    private boolean CanMove(int x, int y, Walls wall) {

        // cannot move, we've hit a wall
        if (mazemap[x][y].contains(wall)) {
            return false;
        }
        int x1 = x;
        int y1 = y;

        // Find out where we are moving to
        switch (wall) {
            case North:
                y1--;
                break;
            case South:
                y1++;
                break;
            case East:
                x1++;
                break;
            case West:
                x1--;
                break;
        }

        // return false if we are not on the map
        if (x1 < 0 || x1 >= width) {
            // Wrap around if circular
            if (bMapCircular) {
                x1 = (x1 < 0) ? width - 1 : 0;
            } else {
                return (false);
            }
        }

        if (y1 < 0 || y1 >= height) {
            return (false);
        }
        //return false of we have been on this square before, when seraching
        //  for a solution, don't want to go over old ground
        if (!mazemap[x1][y1].contains(Walls.SolveAvail)) {
            return false;
        }
        return (true);
    }

    boolean FindSolution(int fromx, int fromy, int tox, int toy) {
        if (fromx < 0 || fromx >= width) {
            // Wrap around if circular
            fromx = (fromx < 0) ? width - 1 : 0;
        }

        // mark the current square as searched
        mazemap[fromx][fromy].remove(Walls.SolveAvail);

        // Have we found the solution
        if (fromx == tox && fromy == toy) {
            solution.add(new Point(fromx, fromy));
            return (true);
        }
        //Check the 4 possible directions, recursely calling 
        //  find solution on each one, until we have a solution
        if (CanMove(fromx, fromy, Walls.North)) {
            if (FindSolution(fromx, fromy - 1, tox, toy)) {
                solution.add(new Point(fromx, fromy));
                return (true);
            }
        }
        if (CanMove(fromx, fromy, Walls.South)) {
            if (FindSolution(fromx, fromy + 1, tox, toy)) {
                solution.add(new Point(fromx, fromy));
                return (true);
            }
        }
        if (CanMove(fromx, fromy, Walls.East)) {
            if (FindSolution(fromx + 1, fromy, tox, toy)) {
                solution.add(new Point(fromx, fromy));
                return (true);
            }
        }
        if (CanMove(fromx, fromy, Walls.West)) {
            if (FindSolution(fromx - 1, fromy, tox, toy)) {
                solution.add(new Point(fromx, fromy));
                return (true);
            }
        }
        // This is a dead end
        return (false);
    }

    // Create the map by repeatedly breaking down walls
    private void Process(int x, int y) {
        Walls w = SelectWall(x, y);

        while (w != Walls.Available) {

            Point pt = BreakWall(x, y, w);

            Process(pt.x, pt.y);

            // Select an available direction
            w = SelectWall(x, y);
        }

    }

    // Get the maze width
    public int getWidth() {
        return width;
    }

    // get the maze height
    public int getHeight() {
        return height;
    }

    // determine if there is a wall at a point and direction.  Used in drawing the maze
    public boolean IsWall(int x, int y, Walls wall) {
        return (mazemap[x][y].contains(wall));
    }

    //  Create a new maze
    public void CreateMaze(boolean circular) {
        // Create a maze map
        ClearMap();

        // Create the maze
        bMapCircular = circular;
        GenerateMap();

        // pick random entrance and exit points and break the entry and exit points
        entrance = rand.nextInt(width);
        exit = rand.nextInt(width);

        BreakWall(exit, 0, Walls.North);
        BreakWall(entrance, height - 1, Walls.South);

        // find the solution to the maze we have created
        solution = new ArrayList();
        FindSolution(entrance, height - 1, exit, 0);
    }

    //  Start create the map
    private void GenerateMap() {
        // select a random start point
        int x = rand.nextInt(width);
        int y = rand.nextInt(height);
        mazemap[x][y].remove(Walls.Available);

        // Start creating the maze from that point
        Process(x, y);
        bMapCreated = true;
    }
    
    // reset the 3d solution line, when we are at the entrance of the maze
    public void ResetSolution(){
        solution3D = (ArrayList<Point>) solution.clone();
    }

    // Is the maze circular or rectangular
    public boolean isCircular() {
        return bMapCircular;
    }

    // sets the current position
    public void setPos(int x, int y) {
        xpos = x;
        ypos = y;
    }

    // Get the current position
    public Point getLocation() {
        return new Point(xpos, ypos);
    }

    // Are we at the start
    public boolean AtStart() {
        return ypos == height;
    }

    // Have we completed the maze
    public boolean Completed() {
        return ypos == -1;
    }

    // Constructor - seed the random number generator
    public Maze() {
        bMapCreated = false;
        rand = new Random(System.currentTimeMillis());
    }
}
