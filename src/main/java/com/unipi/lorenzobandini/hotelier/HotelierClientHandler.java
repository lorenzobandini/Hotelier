package com.unipi.lorenzobandini.hotelier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Type;

import javax.xml.bind.DatatypeConverter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.unipi.lorenzobandini.hotelier.model.Hotel;
import com.unipi.lorenzobandini.hotelier.model.HotelReviews;
import com.unipi.lorenzobandini.hotelier.model.Ratings;
import com.unipi.lorenzobandini.hotelier.model.Review;
import com.unipi.lorenzobandini.hotelier.model.User;

public class HotelierClientHandler implements Runnable {

    String reset = "\u001B[0m";
    String yellow = "\u001B[33m";
    String green = "\u001B[32m";
    String red = "\u001B[31m";
    String blue = "\u001B[34m";

    private Socket clientSocket;
    private Gson gson;

    private String currentUsername;
    private boolean isLogged = false;

    private final Object lockUsers = new Object();
    private final Object lockHotels;
    private final Object lockReviews;

    public HotelierClientHandler(Socket clientSocket, Gson gson, Object lockHotels, Object lockReviews) {
        this.clientSocket = clientSocket;
        this.gson = gson;
        this.lockHotels = lockHotels;
        this.lockReviews = lockReviews;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String username, password, hotelName, city, clientMessage;
            writer.println();
            writer.println(yellow +
                    "██╗  ██╗ ██████╗ ████████╗███████╗██╗     ██╗███████╗██████╗ \n" +
                    "██║  ██║██╔═══██╗╚══██╔══╝██╔════╝██║     ██║██╔════╝██╔══██╗\n" +
                    "███████║██║   ██║   ██║   █████╗  ██║     ██║█████╗  ██████╔╝\n" +
                    "██╔══██║██║   ██║   ██║   ██╔══╝  ██║     ██║██╔══╝  ██╔══██╗\n" +
                    "██║  ██║╚██████╔╝   ██║   ███████╗███████╗██║███████╗██║  ██║\n" +
                    "╚═╝  ╚═╝ ╚═════╝    ╚═╝   ╚══════╝╚══════╝╚═╝╚══════╝╚═╝  ╚═╝\n" +
                    "                                                              " + reset);
            writer.println(homeMessage());

            while ((clientMessage = reader.readLine()) != null) {
                switch (clientMessage) {
                    case "1": // register
                        if (this.isLogged) {
                            writer.println(red + "You have to logout to register" + reset);
                            break;
                        }
                        writer.println(yellow + "Insert username for registration:" + reset);
                        username = reader.readLine();
                        writer.println(yellow + "Insert password for registration:" + reset);
                        password = reader.readLine();
                        register(username, password, writer);
                        break;

                    case "2": // login
                        if (this.isLogged) {
                            writer.println(red + "You already logged in" + reset);
                            break;
                        }
                        writer.println(yellow + "Insert username for login:" + reset);
                        username = reader.readLine();
                        writer.println(yellow + "Insert password for login:" + reset);
                        password = reader.readLine();
                        login(username, password, writer);
                        break;

                    case "3": // logout
                        if (!this.isLogged) {
                            writer.println(red + "You have to login to logout" + reset);
                            break;
                        }
                        logout(this.currentUsername, writer);
                        this.currentUsername = null;
                        break;

                    case "4": // searchHotel
                        writer.println(yellow + "Insert the city of the hotel you want to search:" + reset);
                        city = reader.readLine();
                        writer.println(yellow + "Insert the hotel name of the hotel you want to search:" + reset);
                        hotelName = reader.readLine();
                        searchHotel(hotelName, city, writer);
                        break;

                    case "5": // searchAllHotels
                        writer.println(yellow + "Insert the city of the hotels you want to search:" + reset);
                        city = reader.readLine();
                        searchAllHotels(city, writer);
                        break;

                    case "6": // insertReview
                        if (!this.isLogged) {
                            writer.println(red + "You have to login to insert a review" + reset);
                            break;
                        }
                        writer.println(yellow + "Insert the city of the hotel you want to review:" + reset);
                        city = reader.readLine();
                        writer.println(yellow + "Insert the hotel name of the hotel you want to review:" + reset);
                        hotelName = reader.readLine();
                        if (checkHotel(hotelName, city)) {
                            writer.println(red + "Hotel not found!" + reset);
                            break;
                        }

                        int globalScore = getScore("global ", writer, reader);

                        int rateCleaning = getScore("cleaning ", writer, reader);
                        int ratePosition = getScore("position ", writer, reader);
                        int rateServices = getScore("services ", writer, reader);
                        int rateQuality = getScore("quality ", writer, reader);

                        Ratings ratings = new Ratings((float) rateCleaning, (float) ratePosition, (float) rateServices,
                                (float) rateQuality);

                        insertReview(hotelName, city, globalScore, ratings, writer);
                        break;

                    case "7": // showMyBadges
                        if (!this.isLogged) {
                            writer.println(red + "You have to login to see your badge" + reset);
                            break;
                        }
                        showMyBadge(writer);
                        break;

                    case "8": // exit
                        writer.println(yellow + "Type exit to confirm the exit" + reset);
                        if (reader.readLine().equals("exit")) {
                            if (this.isLogged) {
                                logout(this.currentUsername, writer);
                            }
                            clientMessage = "exit";
                            break;
                        }
                        writer.println(red + "Exit aborted" + reset);
                        break;

                    default:
                        writer.println(red + "Command not found" + reset);
                        break;
                }
                if (clientMessage.equals("exit")) {
                    break;
                }
                writer.println(homeMessage());
            }

            reader.close();
            writer.close();
            clientSocket.close();
            System.out.println("Client " + clientSocket.getRemoteSocketAddress() + " disconnected");

        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    private String homeMessage() {
        if (this.isLogged) {
            return (yellow + this.currentUsername
                    + ", welcome to the Hotel Booking System HOTELIER!\nThe commands are:\n[1] Register\n[2] Login\n[3] Logout\n[4] Search a hotel\n[5] Search all the hotels in a city\n[6] Insert a review for a hotel\n[7] Show my badge\n[8] Exit"
                    + reset);
        } else {
            return (yellow
                    + "Welcome to the Hotel Booking System HOTELIER!\nThe commands that you can do are (for some of these you will have to login):\n[1] Register\n[2] Login\n[3] Logout (login required)\n[4] Search a hotel\n[5] Search all the hotels in a city\n[6] Insert a review for a hotel (login required)\n[7] Show my badge (login required)\n[8] Exit"
                    + reset);
        }

    }

    private void register(String username, String password, PrintWriter writer)
            throws NoSuchAlgorithmException, IOException {
        synchronized (lockUsers) {
            if (username.equals("") || password.equals("")) {
                writer.println(red + "Username or password cannot be empty!" + reset);
                return;
            }
            User user = new User(username, hashPassword(password), false, "Recensore", 0);

            List<User> users = getListUsers();
            File file = new File("src/main/resources/Users.json");

            for (User existingUser : users) {
                if (existingUser.getUsername().equals(username)) {
                    writer.println(red + "Username already exists! Chose another one!" + reset);
                    return;
                }
            }

            // Aggiungi il nuovo utente alla lista
            users.add(user);

            // Riscrivi il file con la lista aggiornata
            FileWriter fileWriter = new FileWriter(file);
            this.gson.toJson(users, fileWriter);
            fileWriter.flush();
            fileWriter.close();
            writer.println(green + "User registered successfully!" + reset);
        }
    }

    private void login(String username, String password, PrintWriter writer)
            throws NoSuchAlgorithmException, IOException {
        synchronized (lockUsers) {

            File file = new File("src/main/resources/Users.json");
            List<User> users = getListUsers();

            for (User existingUser : users) {
                if (existingUser.getUsername().equals(username)) {
                    if (existingUser.isLogged()) {
                        writer.println(red + "User already logged in" + reset);
                        return;
                    }
                    if (existingUser.getHashPassword().equals(hashPassword(password))) {
                        existingUser.setLogged(true);
                        FileWriter fileWriter = new FileWriter(file);
                        gson.toJson(users, fileWriter);
                        fileWriter.flush();
                        fileWriter.close();
                        this.isLogged = true;
                        this.currentUsername = username;
                        writer.println(green + "Login successful" + reset);
                        return;
                    } else {
                        writer.println(red + "Incorrect password" + reset);
                        return;
                    }
                }
            }
            writer.println(red + "Username not found" + reset);
        }
    }

    private void logout(String username, PrintWriter writer) throws IOException {
        synchronized (lockUsers) {
            File file = new File("src/main/resources/Users.json");
            List<User> users = getListUsers();

            for (User existingUser : users) {
                if (existingUser.getUsername().equals(username)) {
                    // Imposta isLogged a false e aggiorna il file JSON
                    existingUser.setLogged(false);
                    FileWriter fileWriter = new FileWriter(file);
                    this.gson.toJson(users, fileWriter);
                    fileWriter.flush();
                    fileWriter.close();
                    this.isLogged = false;
                    this.currentUsername = null;
                    writer.println(green + "Logout successful" + reset);
                    return;
                }
            }
        }
    }

    private void searchHotel(String hotelName, String city, PrintWriter writer) throws IOException {
        synchronized (lockHotels) {
            List<Hotel> hotels = getListHotels();

            for (Hotel hotel : hotels) {
                if (hotel.getName().equals(hotelName) && hotel.getCity().equals(city)) {
                    printHotelStat(hotel, writer);
                    writer.println(yellow + "---------------------------------------------" + reset);
                    return;
                }
            }
            writer.println(red + "Hotel not found" + reset);

        }
    }

    private void searchAllHotels(String city, PrintWriter writer) throws IOException {
        synchronized (lockHotels) {
            List<Hotel> hotels = getListHotels();
            for (Hotel hotel : hotels) {
                if (hotel.getCity().equals(city)) {
                    printHotelStat(hotel, writer);
                }
            }
            writer.println(yellow + "---------------------------------------------" + reset);
        }

    }

    private void insertReview(String hotelName, String city, float globalScore, Ratings ratings, PrintWriter writer)
            throws IOException {
        synchronized (lockReviews) {
            List<HotelReviews> allHotelsReviews = getListReviews();
            Review review = new Review(this.currentUsername, globalScore, ratings);

            for (HotelReviews hotelReviews : allHotelsReviews) {
                if (hotelReviews.getHotelName().equals(hotelName) && hotelReviews.getCity().equals(city)) {
                    hotelReviews.addReview(review);
                    FileWriter fileWriter = new FileWriter("src/main/resources/Reviews.json");
                    this.gson.toJson(allHotelsReviews, fileWriter);
                    fileWriter.flush();
                    fileWriter.close();
                    updateHotelRate(hotelName, city);
                    updateBadge();
                    writer.println(green + "Review added successfully" + reset);
                    return;
                }
            }
            HotelReviews hotelReviews = new HotelReviews(hotelName, city);
            hotelReviews.addReview(review);
            allHotelsReviews.add(hotelReviews);
            FileWriter fileWriter = new FileWriter("src/main/resources/Reviews.json");
            this.gson.toJson(allHotelsReviews, fileWriter);
            fileWriter.flush();
            fileWriter.close();
            updateHotelRate(hotelName, city);
            updateBadge();
            writer.println(green + "Review added successfully" + reset);
        }
    }

    private void showMyBadge(PrintWriter writer) throws IOException {
        synchronized (lockUsers) {
            List<User> users = getListUsers();

            for (User existingUser : users) {
                if (existingUser.getUsername().equals(this.currentUsername)) {
                    writer.println(blue + "Your badge is: " + existingUser.getBadge() + reset);
                    return;
                }
            }
        }
    }

    private List<User> getListUsers() throws IOException {

        File file = new File("src/main/resources/Users.json");
        Type userListType = new TypeToken<ArrayList<User>>() {
        }.getType();
        List<User> users = new ArrayList<>();

        if (file.length() != 0) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                users = this.gson.fromJson(br, userListType);
            }
        }
        return users;
    }

    private List<Hotel> getListHotels() throws IOException {
        File file = new File("src/main/resources/Hotels.json");
        Type hotelListType = new TypeToken<ArrayList<Hotel>>() {
        }.getType();
        List<Hotel> hotels = new ArrayList<>();

        if (file.length() != 0) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                hotels = this.gson.fromJson(br, hotelListType);
            }
        }
        return hotels;
    }

    private List<HotelReviews> getListReviews() throws IOException {
        File file = new File("src/main/resources/Reviews.json");
        Type reviewsListType = new TypeToken<ArrayList<HotelReviews>>() {
        }.getType();
        List<HotelReviews> reviews = new ArrayList<>();

        if (file.length() != 0) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                reviews = this.gson.fromJson(br, reviewsListType);
            }
        }

        return reviews;
    }

    private void printHotelStat(Hotel hotel, PrintWriter writer) {
        writer.println(yellow + "---------------------------------------------" + reset);
        writer.println(yellow + "Hotel found: " + blue + hotel.getName() + reset);
        writer.println(yellow + "Description: " + blue + hotel.getDescription() + reset);
        writer.println(yellow + "City: " + blue + hotel.getCity() + reset);
        writer.println(yellow + "Phone: " + blue + hotel.getPhone() + reset);
        writer.println(yellow + "Services:" + reset);
        for (String service : hotel.getServices()) {
            writer.println(blue + "    " + service + reset);
        }
        writer.println(yellow + "Rate: " + blue + hotel.getRate() + reset);
        writer.println(yellow + "Ratings:" + reset);
        writer.println(yellow + "    Cleaning: " + blue + hotel.getRatings().getCleaning() + reset);
        writer.println(yellow + "    Position: " + blue + hotel.getRatings().getPosition() + reset);
        writer.println(yellow + "    Services: " + blue + hotel.getRatings().getServices() + reset);
        writer.println(yellow + "    Quality: " + blue + hotel.getRatings().getQuality() + reset);
    }

    private boolean checkHotel(String hotelName, String city) throws IOException {
        synchronized (lockHotels) {
            List<Hotel> hotels = getListHotels();

            for (Hotel hotel : hotels) {
                if (hotel.getName().equals(hotelName) && hotel.getCity().equals(city)) {
                    return false;
                }
            }
            return true;
        }
    }

    private void updateBadge() throws IOException {
        synchronized (lockUsers) {
            List<User> users = getListUsers();

            for (User existingUser : users) {
                if (existingUser.getUsername().equals(this.currentUsername)) {
                    existingUser.addReview();
                    FileWriter fileWriter = new FileWriter("src/main/resources/Users.json");
                    this.gson.toJson(users, fileWriter);
                    fileWriter.flush();
                    fileWriter.close();
                    return;
                }
            }
        }
    }

    private void updateHotelRate(String hotelName, String city) {
        synchronized (lockReviews) {
            synchronized (lockHotels) {
                try {
                    List<HotelReviews> allHotelsReviews = getListReviews();
                    List<Hotel> hotels = getListHotels();

                    for (Hotel hotel : hotels) {
                        if (hotel.getName().equals(hotelName) && hotel.getCity().equals(city)) {
                            float globalScore = 0.0f;
                            float cleaningScore = 0.0f;
                            float positionScore = 0.0f;
                            float servicesScore = 0.0f;
                            float qualityScore = 0.0f;
                            int reviews = 0;

                            for (HotelReviews hotelReviews : allHotelsReviews) {
                                if (hotelReviews.getHotelName().equals(hotelName)
                                        && hotelReviews.getCity().equals(city)) {
                                    for (Review review : hotelReviews.getReviews()) {
                                        globalScore += review.getGlobalScore();
                                        cleaningScore += review.getRatings().getCleaning();
                                        positionScore += review.getRatings().getPosition();
                                        servicesScore += review.getRatings().getServices();
                                        qualityScore += review.getRatings().getQuality();
                                        reviews++;
                                    }
                                }
                            }

                            if (reviews > 0) {
                                hotel.setRate(globalScore / reviews);
                                hotel.getRatings().setCleaning(cleaningScore / reviews);
                                hotel.getRatings().setPosition(positionScore / reviews);
                                hotel.getRatings().setServices(servicesScore / reviews);
                                hotel.getRatings().setQuality(qualityScore / reviews);
                            }

                        }
                    }
                    FileWriter fileWriter = new FileWriter("src/main/resources/Hotels.json");
                    this.gson.toJson(hotels, fileWriter);
                    fileWriter.flush();
                    fileWriter.close();
                    fileWriter.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private int getScore(String scoreType, PrintWriter writer, BufferedReader reader) throws IOException {
        int score = 0;
        while (score < 1 || score > 5) {
            writer.println(yellow + "Insert the " + scoreType + "score of the hotel from 1 to 5" + reset);
            try {
                score = Integer.parseInt(reader.readLine());
                if (score < 1 || score > 5) {
                    writer.println(red + "Invalid score!" + reset);
                }
            } catch (NumberFormatException e) {
                writer.println(red + "Invalid score!" + reset);
            }
        }
        return score;
    }

    private String hashPassword(String password) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(password.getBytes());
        byte[] digest = md.digest();
        return DatatypeConverter.printHexBinary(digest).toLowerCase();
    }
}