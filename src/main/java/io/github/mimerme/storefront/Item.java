package io.github.mimerme.storefront;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import io.github.mimerme.data.Category;
import io.github.mimerme.yaros.Utils;

// IMPORTANT: NOT A GSON thing
// Buyer perspective
public class Item {
	public String id = generateUUID();
	public String name;
	public Category category;
	public double buyingPrice;
	
	static String generateUUID() {
		String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	    Random random = new Random();
	        
	    // 1. Determine a random length between 1 and 5
	    int length = 4;
	        
	    StringBuilder sb = new StringBuilder(length);
	        
	    // 2. Build the string character by character
	    for (int i = 0; i < length; i++) {
	    	int randomIndex = random.nextInt(characters.length());
	        sb.append(characters.charAt(randomIndex));
	    }
	        
	    String randomString = sb.toString();
	    return randomString;
	 }
	
	public static ArrayList<Item> fromFile() {
		ArrayList<Item> items = new ArrayList<Item>();
		String path = Utils.cwd() + "./database/items.csv";
		Utils.touch(path);
		
		BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(path));
            String line;
            
            // Read line by line until readLine() returns null (End Of File)
            while ((line = reader.readLine()) != null) {
            	items.add(Item.fromLine(line));
            }
            
            
            
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // In older Java, you must manually close streams in a 'finally' block
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }		
		
		/*
		 * List<Item> items = new ArrayList<Item>();
		 * 
		 * Path startDir = Paths.get(Utils.cwd() + "./database/items.csv");
		 * 
		 * try (Stream<Path> stream = Files.walk(startDir)) {
		 * stream.filter(Files::isRegularFile) // Exclude directories, keep only files
		 * .forEach(Item::fromLine)); } catch (IOException e) { e.printStackTrace(); }
		 */	
		return items;
	}
	
	public static Item fromLine(String line) {
		String splits[] = line.split(",");
		Item out = new Item();
		out.name = splits[0];
		out.category = Category.valueOf(splits[1].trim().toUpperCase());
		out.buyingPrice = Double.parseDouble(splits[2]);
		
		return out;
	}
}
