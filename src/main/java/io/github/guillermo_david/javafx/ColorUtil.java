package io.github.guillermo_david.javafx;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class ColorUtil {
	
	public static String colorForTag(String name, boolean dark) {
	    // hash → [0..359] grados de tono
	    int h = Math.abs(name.hashCode());
	    double hue = h % 360;

	    // S y L ajustados por tema
	    double sat = dark ? 0.55 : 0.60;
	    double lum = dark ? 0.45 : 0.55;

	    return hslToHex(hue, sat, lum);
	}

	private static String hslToHex(double h, double s, double l) {
	    h = (h % 360 + 360) % 360;
	    double c = (1 - Math.abs(2*l - 1)) * s;
	    double x = c * (1 - Math.abs((h/60) % 2 - 1));
	    double m = l - c/2;

	    double r=0,g=0,b=0;
	    if (0<=h && h<60)   { r=c; g=x; b=0; }
	    else if (60<=h && h<120) { r=x; g=c; b=0; }
	    else if (120<=h && h<180){ r=0; g=c; b=x; }
	    else if (180<=h && h<240){ r=0; g=x; b=c; }
	    else if (240<=h && h<300){ r=x; g=0; b=c; }
	    else { r=c; g=0; b=x; }

	    int R = (int)Math.round((r+m)*255);
	    int G = (int)Math.round((g+m)*255);
	    int B = (int)Math.round((b+m)*255);
	    return String.format("#%02X%02X%02X", R,G,B);
	}

	public static String bestTextOn(String hexBg) {
	    // YIQ simple para elegir blanco/negro
	    int r = Integer.valueOf(hexBg.substring(1,3), 16);
	    int g = Integer.valueOf(hexBg.substring(3,5), 16);
	    int b = Integer.valueOf(hexBg.substring(5,7), 16);
	    int yiq = (int)((r*299 + g*587 + b*114) / 1000);
	    return yiq >= 160 ? "#111111" : "#FFFFFF";
	}

}
