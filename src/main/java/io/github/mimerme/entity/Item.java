package io.github.mimerme.entity;

import io.github.mimerme.data.Category;

// IMPORTANT: NOT A GSON thing
// Buyer perspective
public class Item {
	public String id;
	public String name;
	public Category category;
	public double buyingPrice;
}
