package movement;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import movement.map.SimMap;

import core.Coord;
import core.Settings;

public class NoMovement extends MovementModel {

	/** number of map files -setting id ({@value} ) */
	public static final String NO_MOVEMENT_NS = "NoMovement";
	public static final String FILE_S = "locFile";
	private Settings settings;
	private Coord[] locs;

	public NoMovement(Settings settings) {
		super(settings);
		this.settings = settings;
		Settings subSetting = new Settings(NO_MOVEMENT_NS);
		String pathFile = settings.getSetting(FILE_S);
		FileReader fr;
		try {
			fr = new FileReader(pathFile);
			BufferedReader br = new BufferedReader(fr);
			String s = br.readLine();
			int n = Integer.valueOf(s);
			locs = new Coord[n];
			int count = 0;
			while ((s = br.readLine()) != null) {
				String[] tmp = s.split(",");
				int x = Integer.valueOf(tmp[0]);
				int y = Integer.valueOf(tmp[1]);
				locs[count] = new Coord(x, y);
				count++;
			}
			fr.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public Coord getInitialLocation() {
		return null;
	}
	
	public Coord getInitialLocation(int address){
		if (address < locs.length){
			return locs[address];
		} else {
			return new Coord(0, 0);
		}
	}

	@Override
	public Path getPath() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MovementModel replicate() {
		return new NoMovement(this.settings);
	}

}
