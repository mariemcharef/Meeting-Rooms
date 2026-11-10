package com.nordia.rooms;

import java.util.List;

public record Room(int id, String name, int capacity, int floor, List<String> equipment) {
}
