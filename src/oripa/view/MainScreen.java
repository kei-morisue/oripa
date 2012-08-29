/**
 * ORIPA - Origami Pattern Editor 
 * Copyright (C) 2005-2009 Jun Mitani http://mitani.cs.tsukuba.ac.jp/

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package oripa.view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.vecmath.Vector2d;
import javax.xml.bind.annotation.XmlElementDecl.GLOBAL;

import oripa.Config;
import oripa.Constants;
import oripa.Doc;
import oripa.ORIPA;
import oripa.geom.GeomUtil;
import oripa.geom.Line;
import oripa.geom.OriFace;
import oripa.geom.OriLine;
import oripa.geom.OriVertex;
import oripa.geom.RectangleClipper;
import oripa.paint.ElementSelector;
import oripa.paint.Globals;
import oripa.paint.MouseContext;


public class MainScreen extends JPanel
        implements MouseListener, MouseMotionListener, MouseWheelListener, ActionListener, ComponentListener{

    private boolean bDrawFaceID = false;
    private Image bufferImage;
    private Graphics2D bufferg;
    private Point2D preMousePoint; // Screen coordinates
    private Point2D currentMouseDraggingPoint = null;
    private Point2D.Double currentMousePointLogic = new Point2D.Double(); // Logic coordinates
    private double scale;
    private double transX;
    private double transY;
    // Temporary information when editing
    private OriLine prePickLine = null;
    private Vector2d prePickV = null;
    private Vector2d preprePickV = null;
    private Vector2d prepreprePickV = null;
    private Vector2d pickCandidateV = null;
    private OriLine pickCandidateL = null;
    private ArrayList<Vector2d> tmpOutline = new ArrayList<>(); // Contour line when editing
    private boolean dispGrid = true;
    // Affine transformation information
    private Dimension preSize;
    private AffineTransform affineTransform = new AffineTransform();
    private ArrayList<Vector2d> crossPoints = new ArrayList<>();
    private JPopupMenu popup = new JPopupMenu();
    private JMenuItem popupItem_DivideFace = new JMenuItem("Dividing face");
    private JMenuItem popupItem_FlipFace = new JMenuItem("Flipping face");

    public MainScreen() {
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
        addComponentListener(this);

        scale = 1.5;
        setBackground(Color.white);

        popupItem_DivideFace.addActionListener(this);
        popup.add(popupItem_DivideFace);
        popupItem_FlipFace.addActionListener(this);
        popup.add(popupItem_FlipFace);
        preSize = getSize();
    }

    public void drawModel(Graphics2D g2d) {
        if (Config.FOR_STUDY) {
            if (bDrawFaceID) {
                g2d.setColor(Color.BLACK);
                for (OriFace face : ORIPA.doc.faces) {
                    g2d.drawString("" + face.tmpInt, (int) face.getCenter().x,
                            (int) face.getCenter().y);
                }
            }

            g2d.setColor(new Color(255, 210, 220));
            for (OriFace face : ORIPA.doc.faces) {
                if (face.tmpInt2 == 0) {
                    g2d.setColor(Color.RED);
                    g2d.fill(face.preOutline);
                } else {
                    g2d.setColor(face.color);
                }

                if (face.hasProblem) {
                    g2d.setColor(Color.RED);
                } else {
                    if (face.faceFront) {
                        g2d.setColor(new Color(255, 200, 200));
                    } else {
                        g2d.setColor(new Color(200, 200, 255));
                    }
                }

                g2d.fill(face.preOutline);
            }

            g2d.setColor(Color.BLACK);
         
            
            for (OriFace face : ORIPA.doc.faces) {
                g2d.drawString("" + face.z_order, (int) face.getCenter().x,
                        (int) face.getCenter().y);
            }

            g2d.setColor(Color.RED);
            for (OriVertex v : ORIPA.doc.vertices) {
                if (v.hasProblem) {
                    g2d.fill(new Rectangle2D.Double(v.p.x - 8.0 / scale,
                            v.p.y - 8.0 / scale, 16.0 / scale, 16.0 / scale));
                }
            }
        }
    }

    // update actual AffineTransform
    private void updateAffineTransform() {
        affineTransform.setToIdentity();
        affineTransform.translate(getWidth() * 0.5, getHeight() * 0.5);
        affineTransform.scale(scale, scale);
        affineTransform.translate(transX, transY);
        
    }

    
    public Image getCreasePatternImage(){
    	
    	return bufferImage;
    }
    
    
    private void drawLines(Graphics2D g2d, ArrayList<OriLine> lines){
    	
    	ElementSelector selector = new ElementSelector();
        for (OriLine line : lines) {
            if (line.typeVal == OriLine.TYPE_NONE &&!Globals.dispAuxLines) {
                continue;
            }

            if ((line.typeVal == OriLine.TYPE_RIDGE || line.typeVal == OriLine.TYPE_VALLEY)
            		&& !Globals.dispMVLines) {
                continue;
            }
            
        	g2d.setColor(selector.selectColorByLineType(line.typeVal));
        	g2d.setStroke(selector.selectStroke(line.typeVal));
        	
            if(Globals.mouseAction != null){
            	if(mouseContext.getLines().contains(line) == false){
            		g2d.draw(new Line2D.Double(line.p0.x, line.p0.y, line.p1.x, line.p1.y));
            	}
            }
            else {
            	if (line == prePickLine) {
	                g2d.setColor(Color.RED);
	                g2d.setStroke(Config.STROKE_PICKED);
	            } else if (line == pickCandidateL) {
	                g2d.setColor(Config.LINE_COLOR_CANDIDATE);
	                g2d.setStroke(Config.STROKE_PICKED);
	            }
                g2d.draw(new Line2D.Double(line.p0.x, line.p0.y, line.p1.x, line.p1.y));
            }

        }

    }
    
    void drawVertexRectangles(Graphics2D g2d){
        g2d.setColor(Color.BLACK);
        double vertexDrawSize = 2.0;
        for (OriLine line : ORIPA.doc.lines) {
            if (!Globals.dispAuxLines && line.typeVal == OriLine.TYPE_NONE) {
                continue;
            }
            if (!Globals.dispMVLines && (line.typeVal == OriLine.TYPE_RIDGE
                    || line.typeVal == OriLine.TYPE_VALLEY)) {
                continue;
            }
            Vector2d v0 = line.p0;
            Vector2d v1 = line.p1;

            g2d.fill(new Rectangle2D.Double(v0.x - vertexDrawSize / scale,
                    v0.y - vertexDrawSize / scale, vertexDrawSize * 2 / scale,
                    vertexDrawSize * 2 / scale));
            g2d.fill(new Rectangle2D.Double(v1.x - vertexDrawSize / scale,
                    v1.y - vertexDrawSize / scale, vertexDrawSize * 2 / scale,
                    vertexDrawSize * 2 / scale));
        }

    }
    
    
    // Scaling relative to the center of the screen
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (bufferImage == null) {
            bufferImage = createImage(getWidth(), getHeight());
            bufferg = (Graphics2D) bufferImage.getGraphics();
            updateAffineTransform();
            preSize = getSize();
        }

        // initialize the AffineTransform of bufferg
        bufferg.setTransform(new AffineTransform());

        // Clears the image buffer
        bufferg.setColor(Color.WHITE);
        bufferg.fillRect(0, 0, getWidth(), getHeight());

        // set the AffineTransform of buffer
        bufferg.setTransform(affineTransform);

        Graphics2D g2d = bufferg;

        if (ORIPA.doc.hasModel) {
            drawModel(g2d);
        }
        if (dispGrid) {
            drawGridLine(g2d);
        }

        //g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        g2d.setStroke(Config.STROKE_VALLEY);
        g2d.setColor(Color.black);

        drawLines(g2d, ORIPA.doc.lines);
        
        

        
        // Shows of the outline of the editing
        int outlineVnum = tmpOutline.size();
        if (outlineVnum != 0) {
            g2d.setColor(Color.GREEN);
            g2d.setStroke(Config.STROKE_TMP_OUTLINE);
            for (int i = 0; i < outlineVnum - 1; i++) {
                Vector2d p0 = tmpOutline.get(i);
                Vector2d p1 = tmpOutline.get((i + 1) % outlineVnum);
                g2d.draw(new Line2D.Double(p0.x, p0.y, p1.x, p1.y));
            }

            Vector2d cv = pickCandidateV == null
                    ? new Vector2d(currentMousePointLogic.getX(), currentMousePointLogic.getY())
                    : pickCandidateV;
            g2d.draw(new Line2D.Double(tmpOutline.get(0).x, tmpOutline.get(0).y,
                    cv.x, cv.y));
            g2d.draw(new Line2D.Double(tmpOutline.get(outlineVnum - 1).x,
                    tmpOutline.get(outlineVnum - 1).y, cv.x, cv.y));
        }

        // Drawing of the vertices
        if (Globals.editMode == Constants.EditMode.ADD_VERTEX
                || Globals.editMode == Constants.EditMode.DELETE_VERTEX
                || Globals.dispVertex) {
        	drawVertexRectangles(g2d);
        }


        for (Vector2d v : crossPoints) {
            g2d.setColor(Color.RED);
            g2d.fill(new Rectangle2D.Double(v.x - 5.0 / scale, v.y - 5.0 / scale,
                    10.0 / scale, 10.0 / scale));
        }


        if (Globals.bDispCrossLine) {
            if (!ORIPA.doc.crossLines.isEmpty()) {
                g2d.setStroke(Config.STROKE_TMP_OUTLINE);
                g2d.setColor(Color.MAGENTA);

                for (OriLine line : ORIPA.doc.crossLines) {
                    Vector2d v0 = line.p0;
                    Vector2d v1 = line.p1;

                    g2d.draw(new Line2D.Double(v0.x, v0.y, v1.x, v1.y));

                }
            }
        }

        // Line that links the pair of unsetled faces
        if (Config.FOR_STUDY) {
            if (ORIPA.doc.overlapRelation != null) {
                g2d.setStroke(Config.STROKE_RIDGE);
                g2d.setColor(Color.MAGENTA);
                int size = ORIPA.doc.faces.size();
                for (int i = 0; i < size; i++) {
                    for (int j = i + 1; j < size; j++) {
                        if (ORIPA.doc.overlapRelation[i][j] == Doc.UNDEFINED) {
                            Vector2d v0 = ORIPA.doc.faces.get(i).getCenter();
                            Vector2d v1 = ORIPA.doc.faces.get(j).getCenter();
                            g2d.draw(new Line2D.Double(v0.x, v0.y, v1.x, v1.y));

                        }
                    }
                }
            }
        }

        if (Globals.mouseAction != null) {
        	Globals.mouseAction.onDraw(g2d, mouseContext);

        	g.drawImage(bufferImage, 0, 0, this);

        	Vector2d candidate = mouseContext.pickCandidateV;
            if(candidate != null){
                g.setColor(Color.BLACK);
            	g.drawString("(" + candidate.x + 
            		"," + candidate.y + ")", 0, 10);
            }	
        }
        else {
	        g.drawImage(bufferImage, 0, 0, this);
	
	        if (pickCandidateV != null) {
	            g.setColor(Color.BLACK);
	            g.drawString("(" + pickCandidateV.x + "," + pickCandidateV.y + ")", 0, 10);
	        }
        }
    }
    

    public void setDispGrid(boolean dispGrid) {
        this.dispGrid = dispGrid;
        resetPickElements();
        repaint();
    }

    private void drawGridLine(Graphics2D g2d) {
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.setStroke(Config.STROKE_GRID);

        int lineNum = Globals.gridDivNum;
        double step = ORIPA.doc.size / lineNum;
        for (int i = 1; i < lineNum; i++) {
            g2d.draw(new Line2D.Double(step * i - ORIPA.doc.size / 2.0, -ORIPA.doc.size / 2.0, step * i - ORIPA.doc.size / 2.0, ORIPA.doc.size / 2.0));
            g2d.draw(new Line2D.Double(-ORIPA.doc.size / 2.0, step * i - ORIPA.doc.size / 2.0, ORIPA.doc.size / 2.0, step * i - ORIPA.doc.size / 2.0));
        }
    }

    private boolean pickPointOnLine(Point2D.Double p, Object[] line_vertex) {
        double minDistance = Double.MAX_VALUE;
        OriLine bestLine = null;
        Vector2d nearestPoint = new Vector2d();
        Vector2d tmpNearestPoint = new Vector2d();

        for (OriLine line : ORIPA.doc.lines) {
            double dist = GeomUtil.DistancePointToSegment(new Vector2d(p.x, p.y), line.p0, line.p1, tmpNearestPoint);
            if (dist < minDistance) {
                minDistance = dist;
                bestLine = line;
                nearestPoint.set(tmpNearestPoint);
            }
        }

        if (minDistance / scale < 5) {
            line_vertex[0] = bestLine;
            line_vertex[1] = nearestPoint;
            return true;
        } else {
            return false;
        }
    }

    private Vector2d pickVertex(Point2D.Double p) {
        double minDistance = Double.MAX_VALUE;
        Vector2d minPosition = new Vector2d();

        for (OriLine line : ORIPA.doc.lines) {
            double dist0 = p.distance(line.p0.x, line.p0.y);
            if (dist0 < minDistance) {
                minDistance = dist0;
                minPosition.set(line.p0);
            }
            double dist1 = p.distance(line.p1.x, line.p1.y);
            if (dist1 < minDistance) {
                minDistance = dist1;
                minPosition.set(line.p1);
            }
        }

        if (dispGrid) {
            double step = ORIPA.doc.size / Globals.gridDivNum;
            for (int ix = 0; ix < Globals.gridDivNum + 1; ix++) {
                for (int iy = 0; iy < Globals.gridDivNum + 1; iy++) {
                    double x = -ORIPA.doc.size / 2 + step * ix;
                    double y = -ORIPA.doc.size / 2 + step * iy;
                    double dist = p.distance(x, y);
                    if (dist < minDistance) {
                        minDistance = dist;
                        minPosition.set(x, y);
                    }
                }
            }
        }

        if (minDistance < 10.0 / scale) {
            return minPosition;
        } else {
            return null;
        }
    }

    public void modeChanged() {
        resetPickElements();
        repaint();
    }

    public void resetPickElements() {
        prePickV = null;
        prePickLine = null;
        pickCandidateV = null;
        preprePickV = null;
        prepreprePickV = null;
        crossPoints.clear();
        tmpOutline.clear();
    }

    // returns the OriLine sufficiently closer to point p
    private OriLine pickLine(Point2D.Double p) {
        double minDistance = Double.MAX_VALUE;
        OriLine bestLine = null;

        for (OriLine line : ORIPA.doc.lines) {
            if (Globals.editMode == Constants.EditMode.DELETE_LINE) {
            }
            double dist = GeomUtil.DistancePointToSegment(new Vector2d(p.x, p.y), line.p0, line.p1);
            if (dist < minDistance) {
                minDistance = dist;
                bestLine = line;
            }
        }

        if (minDistance / scale < 10) {
            return bestLine;
        } else {
            return null;
        }
    }

    MouseContext mouseContext = MouseContext.getInstance();
    
    @Override
    public void mouseClicked(MouseEvent e) {
    	
        if (Globals.mouseAction != null) {
            
        	if(javax.swing.SwingUtilities.isRightMouseButton(e)){
        		Globals.mouseAction.onRightClick(
        				mouseContext, affineTransform, e);
        	}
        	else {
        		Globals.mouseAction = Globals.mouseAction.onLeftClick(
        				mouseContext, affineTransform, e);
        	}
        	
        	repaint();
        	return;
        }
    	
        if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
            if (prepreprePickV != null) {
                prepreprePickV = null;
                repaint();
            } else if (preprePickV != null) {
                preprePickV = null;
                repaint();
            } else if (prePickV != null) {
                prePickV = null;
                repaint();
            } else if (!tmpOutline.isEmpty()) {
                tmpOutline.remove(tmpOutline.size() - 1);
                repaint();
            }

        }

        if (Globals.editMode == Constants.EditMode.NONE) {
            return;
        }

        // Gets the logical coordinates of the click
        Point2D.Double clickPoint = new Point2D.Double();
        try {
            affineTransform.inverseTransform(e.getPoint(), clickPoint);
        } catch (Exception ex) {
            return;
        }


        if(false){
//       if (Globals.editMode == Constants.EditMode.ADD_VERTEX) {
//            Object[] line_vertex = new Object[2];
//            if (pickPointOnLine(currentMousePointLogic, line_vertex)) {
//                ORIPA.doc.pushUndoInfo();
//                if (!ORIPA.doc.addVertexOnLine((OriLine) line_vertex[0], (Vector2d) line_vertex[1])) {
//                    ORIPA.doc.loadUndoInfo();
//                }
//                this.pickCandidateV = null;
//                repaint();
//            }
        } else if (Globals.editMode == Constants.EditMode.EDIT_OUTLINE) {
            Vector2d v = pickVertex(currentMousePointLogic);
            // Add the outline being edited
            if (v != null) {
                // Closes if it matches an existing point
                boolean bClose = false;
                for (Vector2d tv : tmpOutline) {
                    if (GeomUtil.Distance(v, tv) < 1) {
                        bClose = true;
                        break;
                    }
                }

                if (bClose) {
                    if (tmpOutline.size() > 2) {
                        closeTmpOutline();
                    }
                } else {
                    tmpOutline.add(v);
                }
                repaint();
            }
            return;
        } else if (Globals.editMode == Constants.EditMode.INPUT_LINE) {

        	if (Globals.lineInputMode == Constants.LineInputMode.COPY_AND_PASTE) {
                Vector2d v = pickVertex(clickPoint);
                if (v != null) {
                    if (!ORIPA.doc.tmpSelectedLines.isEmpty()) {
                        ORIPA.doc.pushUndoInfo();
                        double ox = ORIPA.doc.tmpSelectedLines.get(0).p0.x;
                        double oy = ORIPA.doc.tmpSelectedLines.get(0).p0.y;

                        for (OriLine l : ORIPA.doc.tmpSelectedLines) {
                            double mx = v.x;
                            double my = v.y;

                            double sx = mx + l.p0.x - ox;
                            double sy = my + l.p0.y - oy;
                            double ex = mx + l.p1.x - ox;
                            double ey = my + l.p1.y - oy;

                            OriLine line = new OriLine(sx, sy, ex, ey, l.typeVal);
                            ORIPA.doc.addLine(line);
                        }
                    }
                }


            
            }
        }

        repaint();
    }

    private Vector2d isOnTmpOutlineLoop(Vector2d v) {
        for (int i = 0; i < tmpOutline.size(); i++) {
            Vector2d p0 = tmpOutline.get(i);
            Vector2d p1 = tmpOutline.get((i + 1) % tmpOutline.size());
            if (GeomUtil.DistancePointToLine(v, new Line(p0, new Vector2d(p1.x - p0.x, p1.y - p0.y))) < ORIPA.doc.size * 0.001) {
                return p0;
            }
        }
        return null;
    }

    private boolean isOutsideOfTmpOutlineLoop(Vector2d v) {
        Vector2d p0 = tmpOutline.get(0);
        Vector2d p1 = tmpOutline.get(1);

        boolean CCWFlg = GeomUtil.CCWcheck(p0, p1, v);
        for (int i = 1; i < tmpOutline.size(); i++) {
            p0 = tmpOutline.get(i);
            p1 = tmpOutline.get((i + 1) % tmpOutline.size());
            if (CCWFlg != GeomUtil.CCWcheck(p0, p1, v)) {
                return true;
            }
        }
        return false;
    }

    private void closeTmpOutline() {
        ORIPA.doc.pushUndoInfo();
        // Delete the current outline
        ArrayList<OriLine> outlines = new ArrayList<>();
        for (OriLine line : ORIPA.doc.lines) {
            if (line.typeVal == OriLine.TYPE_CUT) {
                outlines.add(line);
            }
        }
        for (OriLine line : outlines) {
            ORIPA.doc.lines.remove(line);
        }

        // Update the contour line
        int outlineVnum = tmpOutline.size();
        for (int i = 0; i < outlineVnum; i++) {
            OriLine line = new OriLine(tmpOutline.get(i),
                    tmpOutline.get((i + 1) % outlineVnum), OriLine.TYPE_CUT);
            ORIPA.doc.addLine(line);
        }

        // To delete a segment out of the contour
        while (true) {
            boolean bDeleteLine = false;
            for (OriLine line : ORIPA.doc.lines) {
                if (line.typeVal == OriLine.TYPE_CUT) {
                    continue;
                }
                Vector2d OnPoint0 = isOnTmpOutlineLoop(line.p0);
                Vector2d OnPoint1 = isOnTmpOutlineLoop(line.p1);

                if (OnPoint0 != null && OnPoint0 == OnPoint1) {
                    ORIPA.doc.removeLine(line);
                    bDeleteLine = true;
                    break;
                }

                if ((OnPoint0 == null && isOutsideOfTmpOutlineLoop(line.p0))
                        || (OnPoint1 == null && isOutsideOfTmpOutlineLoop(line.p1))) {
                    ORIPA.doc.removeLine(line);
                    bDeleteLine = true;
                    break;
                }
            }
            if (!bDeleteLine) {
                break;
            }
        }

        tmpOutline.clear();
        Globals.editMode = Globals.preEditMode;
        UIPanelSettingDB.getInstance().updateUIPanel();
//        ORIPA.mainFrame.uiPanel.modeChanged();
    }

    @Override
    public void mousePressed(MouseEvent e) {
    	
    	if(Globals.mouseAction != null){
    		Globals.mouseAction.onPressed(mouseContext, affineTransform, e);
    	
    	}    	
    	
        preMousePoint = e.getPoint();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        // Rectangular Selection
    	
    	if(Globals.mouseAction != null){
    		Globals.mouseAction.onReleased(mouseContext, affineTransform, e);
    	}
    	
        currentMouseDraggingPoint = null;
        repaint();
    }

    @Override
    public void mouseEntered(MouseEvent arg0) {
    }

    @Override
    public void mouseExited(MouseEvent arg0) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if ((e.getModifiers() & MouseEvent.BUTTON1_MASK) != 0 && // zoom
                (e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) == MouseEvent.CTRL_DOWN_MASK) {

            double moved = e.getX() - preMousePoint.getX() + e.getY() - preMousePoint.getY();
            scale += moved / 150.0;
            if (scale < 0.01) {
                scale = 0.01;
            }

            preMousePoint = e.getPoint();
            updateAffineTransform();
            repaint();
            
        } else if ((e.getModifiers() & MouseEvent.BUTTON3_MASK) != 0) {
            transX += (double) (e.getX() - preMousePoint.getX()) / scale;
            transY += (double) (e.getY() - preMousePoint.getY()) / scale;
            preMousePoint = e.getPoint();
            updateAffineTransform();
            repaint();
        } else {
            Globals.getMouseAction().onDragged(mouseContext, affineTransform, e);
            repaint();
        }
    }

    
    
    private Point2D.Double getLogicalPoint(Point p){
    	Point2D.Double logicalPoint = new Point2D.Double();
        try {
			affineTransform.inverseTransform(p, logicalPoint);
		} catch (NoninvertibleTransformException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        return logicalPoint;
    }
    
    @Override
    public void mouseMoved(MouseEvent e) {
        // Gets the value of the current logical coordinates of the mouse
   
    	try {
            affineTransform.inverseTransform(e.getPoint(), currentMousePointLogic);
        } catch (Exception ex) {
            return;
        }
        
        
        mouseContext.scale = scale;
        mouseContext.dispGrid = dispGrid;
        mouseContext.mousePoint = getLogicalPoint(e.getPoint());
        
        if (Globals.mouseAction != null) {
        	Globals.mouseAction.onMove(mouseContext, affineTransform, e);
        	//this.mouseContext.pickCandidateV = Globals.mouseAction.onMove(mouseContext, affineTransform, e);
        	repaint();
        	return;
        }

    	if (Globals.editMode == Constants.EditMode.INPUT_LINE) {
            	
           	if (Globals.lineInputMode == Constants.LineInputMode.COPY_AND_PASTE) {
                pickCandidateV = this.pickVertex(currentMousePointLogic);
                repaint();
            }
        } else if (Globals.editMode == Constants.EditMode.EDIT_OUTLINE) {
            pickCandidateV = this.pickVertex(currentMousePointLogic);
            repaint();
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        double scale_ = (100.0 - e.getWheelRotation() * 5) / 100.0;
        scale *= scale_;
        updateAffineTransform();
        repaint();
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
    }

    @Override
    public void componentResized(ComponentEvent arg0) {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        preSize = getSize();

        // Update of the logical coordinates of the center of the screen
        transX = transX - preSize.width * 0.5 + getWidth() * 0.5;
        transY = transY - preSize.height * 0.5 + getHeight() * 0.5;

        // Updating the image buffer
        bufferImage = createImage(getWidth(), getHeight());
        bufferg = (Graphics2D) bufferImage.getGraphics();

        updateAffineTransform();
        repaint();

    }

    @Override
    public void componentMoved(ComponentEvent arg0) {
        // TODO Auto-generated method stub
    }

    @Override
    public void componentShown(ComponentEvent arg0) {
        // TODO Auto-generated method stub
    }

    @Override
    public void componentHidden(ComponentEvent arg0) {
        // TODO Auto-generated method stub
    }


}
