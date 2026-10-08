package io.github.mimerme.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.mimerme.Utils;
import io.github.mimerme.core.Product;
import io.github.mimerme.users.User;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class StoreInformation {
	/// Lazily initialized. this has caused problems
	public static StoreInformation INSTANCE = new StoreInformation();

	public StoreRecord data = new StoreRecord();
	public String dirPath = "./database/cache";
	

	public StoreInformation() {
		// TODO: create `dirPath` if it doesn't exist
	}
	
	public void load() {
		Gson gson = new Gson();
		String path = Utils.cwd() + "/" + this.dirPath + "/information.json";

		/// First time setup
		if  (!Files.exists(Paths.get(path))) {
			System.out.printf("\"%s\" Doesn't Exist. Creating...", path);
			data = new StoreRecord();
			return;
		}

		// Use try-with-resources to automatically close the reader
		try (Reader reader = Files.newBufferedReader(Paths.get(path))) {

			// Parse the JSON file into your container class
			data = gson.fromJson(reader, StoreRecord.class);
			
			if (data == null) {
				throw new RuntimeException("information.json is malformed");
			}

			// Verify it loaded correctly
			System.out.println("Loaded information.json");

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void store() {
		String path = Utils.cwd() + "/" +  this.dirPath + "/information.json";
		Utils.touch(path);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();

		String jsonOutput = gson.toJson(data);
		try (Writer writer = Files.newBufferedWriter(Paths.get(path))) {
			gson.toJson(INSTANCE.data, writer);
			System.out.printf("Successfully wrote JSON to %s%s.json", dirPath, "information");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
