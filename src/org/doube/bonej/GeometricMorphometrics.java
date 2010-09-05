package org.doube.bonej;

/** 
 * Geometric Morphometrics ImageJ plugin Copyright 2010 Michael Doube 
 *
 *This program is free software: you can redistribute it and/or modify
 *it under the terms of the GNU General Public License as published by
 *the Free Software Foundation, either version 3 of the License, or
 *(at your option) any later version.
 *
 *This program is distributed in the hope that it will be useful,
 *but WITHOUT ANY WARRANTY; without even the implied warranty of
 *MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *GNU General Public License for more details.
 *
 *You should have received a copy of the GNU General Public License
 *along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

import java.awt.Component;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;

import javax.media.j3d.View;
import javax.vecmath.Color3f;

import org.doube.bonej.geomorph.Crosshairs;
import org.doube.bonej.geomorph.Landmark;
import org.doube.util.ImageCheck;

import orthoslice.OrthoGroup;
import vib.BenesNamedPoint;
import vib.PointList;
import vib.PointList.PointListListener;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.ScrollbarWithLabel;
import ij.measure.Calibration;
import ij.plugin.Orthogonal_Views;
import ij.plugin.PlugIn;
import ij3d.AxisConstants;
import ij3d.Content;
import ij3d.Image3DUniverse;
import ij3d.UniverseListener;
import ij3d.pointlist.PointListDialog;
import ij3d.pointlist.PointListPanel;
import ij3d.pointlist.PointListShape;

/**
 * 
 * Create landmark sets and analyse them using ImageJ's Orthoslice viewer and 3D
 * Viewer.
 * 
 * Targeting: Generation of labelled landmark sets Storing of landmark
 * definitions Storing of landmarks (in TIFF header?) Viewing & editing
 * landmarks synchronised in orthoviewer and 3D viewer Registering stacks based
 * on landmark sets Analysis? PCA? 4D viewing of landmark sets from different
 * specimens
 * 
 * @author Michael Doube
 * 
 */
public class GeometricMorphometrics implements PlugIn, UniverseListener,
		MouseListener, MouseWheelListener, MouseMotionListener, KeyListener,
		AdjustmentListener, PointListListener {
	private Image3DUniverse univ;
	private Orthogonal_Views orthoViewer;
	private ImagePlus imp, xz_imp, yz_imp;
	private ImageCanvas canvas;
	private ImageWindow win;
	private OrthoGroup ortho3D;
	private Crosshairs crossHairs;
	private Updater updater = new Updater();
	/** Position of the orthoviewers */
	private int x, y, z;
	private int resampling = 2;
	private ArrayList<Landmark> landmarks;
	private PointList pointList;
	private PointListShape plShape;
	private PointListPanel plPanel;
	private PointListDialog pld = null;
	private Calibration cal;

	public void run(String arg) {
		if (!ImageCheck.checkEnvironment())
			return;
		this.imp = IJ.getImage();
		if (this.imp == null)
			return;
		if (!(new ImageCheck()).isMultiSlice(imp)) {
			IJ.error("Multi-slice stack expected");
			return;
		}

		orthoViewer = Orthogonal_Views.getInstance();
		if (orthoViewer == null) {
			new Orthogonal_Views().run("");
			orthoViewer = Orthogonal_Views.getInstance();
		}

		univ = new Image3DUniverse();
		show3DVolume();
		show3DOrtho();
		univ.show();
		canvas = imp.getCanvas();
		xz_imp = orthoViewer.getXZImage();
		yz_imp = orthoViewer.getYZImage();
		win = imp.getWindow();
		pointList = univ.getContent(imp.getTitle()).getPointList();
		univ.getContent(imp.getTitle()).setLandmarkPointSize(
				(float) (imp.getCalibration().pixelWidth * 5));
		plShape = new PointListShape(pointList);
		plShape.setPickable(true);
		plShape.setColor(new Color3f(1.0f, 1.0f, 0.0f));
		plPanel = new PointListPanel("Landmarks", pointList);
		pld = univ.getPointListDialog();
		pld.addPointList("Landmarks", plPanel);
		addListeners();
		landmarks = new ArrayList<Landmark>();
		int[] c = orthoViewer.getCrossLoc();
		cal = imp.getCalibration();
		crossHairs = new Crosshairs(c[0] * cal.pixelWidth, c[1]
				* cal.pixelHeight, c[2] * cal.pixelDepth, univ);
	}

	private void addListeners() {
		// Have to listen to XZ and YZ windows too
		univ.addUniverseListener(this);
		canvas.addMouseListener(this);
		canvas.addMouseMotionListener(this);
		canvas.addKeyListener(this);
		xz_imp.getCanvas().addMouseListener(this);
		xz_imp.getCanvas().addMouseMotionListener(this);
		xz_imp.getCanvas().addKeyListener(this);
		yz_imp.getCanvas().addMouseListener(this);
		yz_imp.getCanvas().addMouseMotionListener(this);
		yz_imp.getCanvas().addKeyListener(this);
		win.addMouseWheelListener((MouseWheelListener) this);
		Component[] c = win.getComponents();
		((ScrollbarWithLabel) c[1])
				.addAdjustmentListener((AdjustmentListener) this);
		pointList.addPointListListener(this);
	}

	private void show3DOrtho() {
		// TODO handle non-8-bit & non-RGB images
		String orthoTitle = "Ortho " + imp.getTitle();
		Content c = univ.getContent(orthoTitle);
		if (c == null) {
			try {
				c = univ.addOrthoslice(imp, (new Color3f(1.0f, 1.0f, 1.0f)),
						orthoTitle, 0, (new boolean[] { true, true, true }),
						resampling);
				c.setName(orthoTitle);
				ortho3D = (OrthoGroup) c.getContent();
				c.setLocked(true);
			} catch (NullPointerException npe) {
				IJ.log("3D Viewer was closed before rendering completed.");
			}
		} else
			c.setVisible(true);
	}

	private void hide3DOrtho() {
		Content c = univ.getContent("Ortho " + imp.getTitle());
		if (c != null && c.isVisible()) {
			c.setVisible(false);
		}
	}

	private void show3DVolume() {
		// TODO handle non-8-bit & non-RGB images
		Content c = univ.getContent(imp.getTitle());
		if (c == null) {
			try {
				Content d = univ.addVoltex(imp, new Color3f(1.0f, 1.0f, 1.0f),
						imp.getTitle(), 0, new boolean[] { true, true, true },
						resampling);
				d.setLocked(true);
				d.setPointListDialog(pld);
				d.setLandmarkPointSize(10.0f);
				pointList = d.getPointList();
				d.showPointList(true);
			} catch (NullPointerException npe) {
				IJ.log("3D Viewer was closed before rendering completed.");
			}
		} else
			c.setVisible(true);
	}

	private void hide3DVolume() {
		Content c = univ.getContent(imp.getTitle());
		if (c != null && c.isVisible()) {
			c.setVisible(false);
		}
	}

	/**
	 * Updates the 2D and 3D orthoviewers so they are displaying the same slices
	 */
	private void syncViewers() {
		// x, y and z are at the last synched position
		// z is 0-based, so z0 = slice 1
		// get the 2D orthoviewer's state
		int[] crossLoc = orthoViewer.getCrossLoc();
		int x2 = crossLoc[0];
		int y2 = crossLoc[1];
		int z2 = crossLoc[2];

		// get the 3D orthoviewer's state
		int x3 = ortho3D.getSlice(AxisConstants.X_AXIS);
		int y3 = ortho3D.getSlice(AxisConstants.Y_AXIS);
		int z3 = ortho3D.getSlice(AxisConstants.Z_AXIS);

		// if the change was in the 2D viewer, update the 3D viewer
		// 2D viewer state must always exactly match (x, y, z)
		// but 3D viewer can be sloppy
		if (x2 != x || y2 != y || z2 != z) {
			x = x2;
			y = y2;
			z = z2;
			if (x < x3 * resampling || x >= (x3 + 1) * resampling
					|| y < y3 * resampling || y >= (y3 + 1) * resampling
					|| z < z3 * resampling || z >= (z3 + 1) * resampling) {
				update(); //update the crosshairs in separate thread
				ortho3D.setSlice(AxisConstants.X_AXIS, x / resampling);
				ortho3D.setSlice(AxisConstants.Y_AXIS, y / resampling);
				ortho3D.setSlice(AxisConstants.Z_AXIS, z / resampling);
			}
			return;
		}

		// if the change was in the 3D viewer, update the 2D viewer
		// 3D viewer state has tolerance to not exactly match (x, y, z) due to
		// resampling
		if (x3 * resampling > x || (x3 + 1) * resampling <= x
				|| y3 * resampling > y || (y3 + 1) * resampling <= y
				|| z3 * resampling > z || (z3 + 1) * resampling <= z) {
			x = x3 * resampling;
			y = y3 * resampling;
			z = z3 * resampling;
			orthoViewer.setCrossLoc(x, y, z);
			return;
		}
		return;
	}

	/**
	 * Display the landmarks in both 3D and 2D viewers
	 */
	private void updateLandmarks() {
		for (Landmark l : landmarks) {
			// check if landmark is already in 3D viewer
			// and add if it is new. Use Bene's Point for 3D visualisation
			String name = l.getName();
			if (pointList.get(name) == null) {
				BenesNamedPoint bnp = new BenesNamedPoint(l.getName(),
						l.getX(), l.getY(), l.getZ());
				pointList.add(bnp);
				pointList.highlight(bnp);
				return;
			} else {

				// point moved in 3D viewer
				// check that the Landmark position matches the point's position
				// in the 3D viewer and update the Landmark, then the 2D viewer,
				// then return

				// point moved in 2D viewer

			}
		}
	}

	@Override
	public void canvasResized() {
		syncViewers();
	}

	@Override
	public void contentAdded(Content c) {
		syncViewers();

	}

	@Override
	public void contentChanged(Content c) {
		syncViewers();

	}

	@Override
	public void contentRemoved(Content c) {
		syncViewers();

	}

	@Override
	public void contentSelected(Content c) {
		syncViewers();

	}

	@Override
	public void transformationFinished(View view) {
		syncViewers();

	}

	@Override
	public void transformationStarted(View view) {
		syncViewers();

	}

	@Override
	public void transformationUpdated(View view) {
		syncViewers();

	}

	@Override
	public void universeClosed() {
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		if (IJ.controlKeyDown()) {
			int[] crossLoc = orthoViewer.getCrossLoc();
			Calibration cal = imp.getCalibration();
			double px = crossLoc[0] * cal.pixelWidth;
			double py = crossLoc[1] * cal.pixelHeight;
			double pz = crossLoc[2] * cal.pixelDepth;
			GenericDialog gd = new GenericDialog("New Landmark");
			gd.addMessage("Adding a new landmark...");
			gd.addStringField("Name", "", 12);
			gd.showDialog();
			if (gd.wasCanceled())
				return;
			String name = gd.getNextString();
			Landmark l = new Landmark(px, py, pz, name);
			landmarks.add(l);
			updateLandmarks();
		}
	}

	@Override
	public void mouseEntered(MouseEvent e) {
	}

	@Override
	public void mouseExited(MouseEvent e) {
	}

	@Override
	public void mousePressed(MouseEvent e) {
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		syncViewers();
	}

	@Override
	public void keyPressed(KeyEvent e) {
	}

	@Override
	public void keyReleased(KeyEvent e) {
		syncViewers();
	}

	@Override
	public void keyTyped(KeyEvent e) {
	}

	@Override
	public void mouseWheelMoved(MouseWheelEvent e) {
		syncViewers();
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		syncViewers();
	}

	@Override
	public void mouseMoved(MouseEvent e) {

	}

	@Override
	public void adjustmentValueChanged(AdjustmentEvent e) {
		syncViewers();
	}

	@Override
	public void added(BenesNamedPoint p) {
		// TODO Auto-generated method stub
	}

	@Override
	public void highlighted(BenesNamedPoint p) {
		// TODO Auto-generated method stub

	}

	@Override
	public void moved(BenesNamedPoint p) {
		updateLandmarks();
	}

	@Override
	public void removed(BenesNamedPoint p) {
		// TODO Auto-generated method stub

	}

	@Override
	public void renamed(BenesNamedPoint p) {
		// TODO Auto-generated method stub

	}

	@Override
	public void reordered() {
		// TODO Auto-generated method stub

	}

	/**
	 * Refresh the output windows. This is done by sending a signal to the
	 * Updater() thread.
	 */
	void update() {
		if (updater != null)
			updater.doUpdate();
	}

	/**
	 * Delegates the repainting of the 3D crosshairs windows to another thread.
	 * 
	 * Borrowed from the helper class in Othogonal_Views
	 * 
	 * @author Albert Cardona
	 */
	private class Updater extends Thread {
		long request = 0;

		// Constructor autostarts thread
		Updater() {
			super("3D Crosshairs Updater");
			setPriority(Thread.MAX_PRIORITY);
			start();
		}

		void doUpdate() {
			if (isInterrupted())
				return;
			synchronized (this) {
				request++;
				notify();
			}
		}

		void quit() {
			IJ.wait(10);
			interrupt();
			synchronized (this) {
				notify();
			}
		}

		public void run() {
			while (!isInterrupted()) {
				try {
					final long r;
					synchronized (this) {
						r = request;
					}
					// Call update from this thread
					if (r > 0) {
						crossHairs.set(x * cal.pixelWidth, y * cal.pixelHeight,
								z * cal.pixelDepth);
						crossHairs.update();
					}
					synchronized (this) {
						if (r == request) {
							request = 0; // reset
							wait();
						}
						// else loop through to update again
					}
				} catch (Exception e) {
				}
			}
		}

	} // Updater class

}
