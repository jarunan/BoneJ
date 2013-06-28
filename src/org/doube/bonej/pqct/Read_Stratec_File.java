/*
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

	N.B.  the above text was copied from http://www.gnu.org/licenses/gpl.html
	unmodified. I have not attached a copy of the GNU license to the source...

	ImageJ Stratec file reader plugin
    Copyright (C) 2011 Timo Rantalainen
 */

package org.doube.bonej.pqct;

import java.io.*;
import java.util.Scanner;

import ij.*;
import ij.io.*;
import ij.gui.*;
import ij.plugin.*;
import ij.measure.*; //Calibration
import javax.activation.*; //UnsupportedDataTypeException

import org.doube.util.UsageReporter;

public class Read_Stratec_File extends ImagePlus implements PlugIn {
	// Global variables
	// Stratec header stuff
	private String PatName;
	private long PatNo;
	private int PatMeasNo;
	private long PatBirth; //
	private long MeasDate; //
	private double VoxelSize; //
	private int PicX0; //
	private int PicY0; //
	private int PicMatrixX; //
	private int PicMatrixY; //
	private String MeasInfo;
	private String Device;
	private String PatID;
	private double ObjLen; //
	private String fileName;
	private String properties;
	private double xSlope;
	private double xInter;

	public Read_Stratec_File() { // Constructor
		// this = null;
	}

	/**
	 * Opens a Stratec pQCT file
	 * 
	 * @param path
	 *            full path to the file
	 * @return ImagePlus containing the calibrated pQCT image
	 * @throws IllegalArgumentException
	 *             if the path is null or the file is not normal
	 */
	public ImagePlus open(String path) throws IllegalArgumentException {
		if (path == null)
			throw new IllegalArgumentException("Path cannot be null");
		File theFile = new File(path);
		if (!theFile.isFile())
			throw new IllegalArgumentException("Path is not a normal file");
		String directory = theFile.getParent() + "/";
		fileName = theFile.getName();
		try {
			read(directory);
			fileInfo();
			return this;
		} catch (Exception err) {
			IJ.error("Stratec file read failed ", err.getMessage());
			return null;
		}
	}

	// Overriding the abstract runnable run method. Apparently plugins run in
	// threads
	public void run(String arg) {
		String directory;

		if (!arg.isEmpty()) {// Called by HandleExtraFileTypes
			File theFile = new File(arg);
			directory = theFile.getParent() + "/";
			fileName = theFile.getName();
		} else {// select file manually
			OpenDialog od = new OpenDialog("Select stratec image (I*.M*)", arg);
			directory = od.getDirectory();
			fileName = od.getFileName();
		}
		if (fileName == null)
			return;
		try {
			read(directory);
			fileInfo();
			if (arg.isEmpty() && this.getHeight() > 0) {
				this.show();
				return;
			}
			if (this.getHeight() < 1)
				return;
		} catch (Exception err) {
			IJ.error("Stratec file read failed ", err.getMessage());
		}
		UsageReporter.reportEvent(this).send();
	}

	private void fileInfo() {
		FileInfo fi = new FileInfo();
		try {
			fi = this.getFileInfo();
		} catch (NullPointerException npe) {
		}
		fi.pixelWidth = VoxelSize;
		fi.pixelHeight = VoxelSize;
		fi.width = PicMatrixX;
		fi.height = PicMatrixY;
		fi.valueUnit = "mm";
		fi.fileName = fileName;
		fi.info = properties;
		fi.fileFormat = fi.RAW;
		fi.compression = fi.COMPRESSION_NONE;
		fi.fileType = fi.GRAY16_SIGNED; //
		this.setFileInfo(fi);
	}

	/**
	 * Read a file in the given directory. The fileName field must be set.
	 * 
	 * @param directory
	 * @throws Exception
	 */
	private void read(String directory) throws Exception {
		File fileIn = new File(directory + fileName);
		long fileLength = fileIn.length();
		byte[] fileData;
		try {
			BufferedInputStream inputStream = new BufferedInputStream(
					new FileInputStream(fileIn));
			DataInputStream dataInputStream = new DataInputStream(inputStream);
			// Allocate memory for reading the file into memory
			fileData = new byte[(int) fileLength];
			// Read the data to memory
			dataInputStream.read(fileData, 0, (int) fileLength);
			// Close the file after reading
			dataInputStream.close();
		} catch (Exception e) {
			throw new UnsupportedDataTypeException("Could not read input file.");
		}
		// Read some data from the file Header
		if (fileLength > 1609) {
			Device = new String(fileData, 1051, fileData[1050]);
		} else {
			throw new UnsupportedDataTypeException(
					"Apparently not a Stratec file, file length < 1609 bytes.");
		}

		if (fileLength > 1609 && Device.toLowerCase().indexOf(".typ") >= 0) {
			readHeader(fileData);
		} else {
			throw new UnsupportedDataTypeException(
					"Apparently not a Stratec file, device string not found.");
		}
		//get attenuation -> BMD calibration from .TYP file
		String typData = getTypData();
		xSlope = getTypValue("XSlope", typData);
		xInter = getTypValue("XInter", typData);

		// Create ImageJ image
		ImagePlus tempImage = NewImage.createShortImage(
				fileName + " " + Double.toString(VoxelSize), PicMatrixX,
				PicMatrixY, 1, NewImage.FILL_BLACK);
		this.setImage(tempImage.getImage());
		this.setProcessor(fileName, tempImage.getProcessor());
		// Set ImageJ image properties
		setProperties(directory);
		short[] pixels = (short[]) this.getProcessor().getPixels();
		int min = (int) Math.pow(2, 16);
		int max = 0;
		for (int j = 0; j < PicMatrixY; ++j) {
			for (int i = 0; i < PicMatrixX; ++i) {
				final int offset = 1609 + 2 * (i + j * PicMatrixX);
				int value = (int) ((short) (((fileData[offset + 1] & 0xFF)) << 8 | ((short) (fileData[offset] & 0xFF)) << 0));

				if (value >= 0) {
					value = (int) -Math.pow(2, 15) + value;
				} else {
					value = (int) Math.pow(2, 15) - 1 + value;
				}
				int tempVal = value & 0xFFFF;
				if (tempVal < min) {
					min = tempVal;
				}
				if (tempVal > max) {
					max = tempVal;
				}
				pixels[i + j * PicMatrixX] = (short) value;
			}
		}
		this.setDisplayRange(min, max);
		Calibration cal = this.getCalibration();
		double[] coefficients = { -32.768, 0.001 };
		cal.setFunction(Calibration.STRAIGHT_LINE, coefficients, "1/cm");
		cal.setUnit("mm");
		cal.pixelWidth = cal.pixelHeight = cal.pixelDepth = VoxelSize;
	}

	private void readHeader(byte[] fileData) {
		// byte stringLength;
		int offset = 12;
		VoxelSize = Double
				.longBitsToDouble((long) (((long) (fileData[offset + 7] & 0xFF)) << 56
						| ((long) (fileData[offset + 6] & 0xFF)) << 48
						| ((long) (fileData[offset + 5] & 0xFF)) << 40
						| ((long) (fileData[offset + 4] & 0xFF)) << 32
						| ((long) (fileData[offset + 3] & 0xFF)) << 24
						| ((long) (fileData[offset + 2] & 0xFF)) << 16
						| ((long) (fileData[offset + 1] & 0xFF)) << 8 | ((long) (fileData[offset + 0] & 0xFF)) << 0));

		offset = 318;
		ObjLen = Double
				.longBitsToDouble((long) (((long) (fileData[offset + 7] & 0xFF)) << 56
						| ((long) (fileData[offset + 6] & 0xFF)) << 48
						| ((long) (fileData[offset + 5] & 0xFF)) << 40
						| ((long) (fileData[offset + 4] & 0xFF)) << 32
						| ((long) (fileData[offset + 3] & 0xFF)) << 24
						| ((long) (fileData[offset + 2] & 0xFF)) << 16
						| ((long) (fileData[offset + 1] & 0xFF)) << 8 | ((long) (fileData[offset + 0] & 0xFF)) << 0));

		offset = 662;
		MeasInfo = new String(fileData, offset + 1, fileData[offset]);

		offset = 986;
		MeasDate = (long) (((long) (fileData[offset + 3] & 0xFF)) << 24
				| ((long) (fileData[offset + 2] & 0xFF)) << 16
				| ((long) (fileData[offset + 1] & 0xFF)) << 8 | ((long) (fileData[offset + 0] & 0xFF)));

		offset = 1085;
		PatMeasNo = ((int) ((int) (fileData[offset + 1] & 0xFF)) << 8 | ((int) (fileData[offset + 0] & 0xFF)));

		offset = 1087;
		PatNo = (long) (((long) (fileData[offset + 3] & 0xFF)) << 24
				| ((long) (fileData[offset + 2] & 0xFF)) << 16
				| ((long) (fileData[offset + 1] & 0xFF)) << 8 | ((long) (fileData[offset + 0] & 0xFF)));

		offset = 1091;
		PatBirth = (long) (((long) (fileData[offset + 3] & 0xFF)) << 24
				| ((long) (fileData[offset + 2] & 0xFF)) << 16
				| ((long) (fileData[offset + 1] & 0xFF)) << 8 | ((long) (fileData[offset + 0] & 0xFF)));

		offset = 1099;
		PatName = new String(fileData, offset + 1, fileData[offset]);

		offset = 1282;
		PatID = new String(fileData, offset + 1, fileData[offset]);

		offset = 1525;
		PicX0 = ((int) ((int) (fileData[offset + 1] & 0xFF)) << 8 | ((int) (fileData[offset + 0] & 0xFF)));

		offset = 1527;
		PicY0 = ((int) ((int) (fileData[offset + 1] & 0xFF)) << 8 | ((int) (fileData[offset + 0] & 0xFF)));

		offset = 1529;
		PicMatrixX = ((int) ((int) (fileData[offset + 1] & 0xFF)) << 8 | ((int) (fileData[offset + 0] & 0xFF)));

		offset = 1531;
		PicMatrixY = ((int) ((int) (fileData[offset + 1] & 0xFF)) << 8 | ((int) (fileData[offset + 0] & 0xFF)));
	}

	private void setProperties(String directory) {
		String[] propertyNames = { "File Name", "File Path", "Pixel Spacing",
				"ObjLen", "MeasInfo", "Acquisition Date", "Device",
				"PatMeasNo", "PatNo", "Patient's Birth Date", "Patient's Name",
				"Patient ID", "PicX0", "PicY0", "Width", "Height", "XSlope", "XInter",
				"Stratec File" };
		String[] propertyValues = { fileName, directory,
				Double.toString(VoxelSize), Double.toString(ObjLen), MeasInfo,
				Long.toString(MeasDate), Device, Integer.toString(PatMeasNo),
				Long.toString(PatNo), Long.toString(PatBirth), PatName, PatID,
				Integer.toString(PicX0), Integer.toString(PicY0),
				Integer.toString(PicMatrixX), Integer.toString(PicMatrixY), 
				Double.toString(xSlope), Double.toString(xInter), "1" };
		properties = new String();
		for (int i = 0; i < propertyNames.length; ++i) {
			properties += propertyNames[i] + ": " + propertyValues[i] + "\n";
		}
		this.setProperty("Info", properties);
	}

	/**
	 * Retrieve the instrument data from the corresponding .typ file
	 * 
	 * @return the instrument settings data as a String
	 */
	private String getTypData() {
		InputStream input = getClass().getResourceAsStream(
				"/org/doube/bonej/pqct/typ/" + Device);
		String inputStreamString = new Scanner(input, "UTF-8").useDelimiter(
				"\\A").next();
		return inputStreamString;
	}

	private double getTypValue(String key, String typString) {
		String[] lines = typString.split("\\r?\\n");
		for (String line : lines) {
			if (line.contains(key)) {
				line = line.replaceAll("\\s", "");
				line = line.substring(line.indexOf("=") + 1);
				return Double.parseDouble(line);
			}
		}
		throw new IllegalArgumentException("Invalid key");
	}
}
