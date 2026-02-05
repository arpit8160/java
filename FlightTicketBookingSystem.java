import java.sql.*;
import java.util.*;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Date;

class FlightTicketBookingSystem {
    static Connection con;
    static Statement st;
    static Scanner sc = new Scanner(System.in);

    // BST for seat management per flight
    static Map<Integer, SeatNode> flightSeatTrees = new HashMap<>();

    public static void main(String[] args) throws Exception {
        String dburl = "jdbc:mysql://localhost:3306/heti";
        String dbuser = "root";
        String dbpass = "";

        try {
            con = DriverManager.getConnection(dburl, dbuser, dbpass);
            st = con.createStatement();

            // Flights table
            String createFlightsTable = "CREATE TABLE IF NOT EXISTS Flights (" +
                    "FlightNumber INT PRIMARY KEY AUTO_INCREMENT, " +
                    "Source VARCHAR(100), " +
                    "Destination VARCHAR(100), " +
                    "DepartureTime TIME, " +
                    "ArrivalTime TIME, " +
                    "FlightDate DATE, " +
                    "AvailableSeats INT, " +
                    "Price DOUBLE)";

            st.executeUpdate(createFlightsTable);

            // Bookings table
            String createBookingsTable = "CREATE TABLE IF NOT EXISTS bookings (" +
                    "BookingID INT PRIMARY KEY AUTO_INCREMENT, " +
                    "FlightNumber INT, " +
                    "PassengerName VARCHAR(100), " +
                    "PassengerEmail VARCHAR(100), " +
                    "SeatsBooked INT, " +
                    "BookingDate DATE, " +
                    "SpecialRequests TEXT)";

            st.executeUpdate(createBookingsTable);

            // Seat assignments table
            String createSeatsTable = "CREATE TABLE IF NOT EXISTS seat_assignments (" +
                    "FlightNumber INT, " +
                    "SeatNumber INT, " +
                    "PassengerName VARCHAR(100), " +
                    "PRIMARY KEY (FlightNumber, SeatNumber))";

            st.executeUpdate(createSeatsTable);

            // Load existing seat assignments into BSTs
            loadSeatAssignments();

            while (true) {
                System.out.println("\n--- Airline Reservation System ---");
                System.out.println("1. Admin Login");
                System.out.println("2. Passenger Login");
                System.out.println("3. Exit");
                System.out.print("Enter your choice: ");
                int a = -1;
                try {
                    a = sc.nextInt();
                } catch (InputMismatchException e) {
                    System.out.println("Invalid input! Please enter a number.");
                    sc.nextLine();
                    continue;
                }
                sc.nextLine();
                switch (a) {
                    case 1:
                        if (adminLogin(sc)) {
                            adminMenu(sc);
                        }
                        break;

                    case 2:
                        if (passengerLogin(sc)) {
                            passengerMenu(sc);
                        }
                        break;

                    case 3:
                        con.close();
                        System.out.println("🙏 -- Thank you -- 🙏");
                        return;

                    default:
                        System.out.println("Invalid choice. Please try again ");
                }
            }
        } catch (SQLException e) {
            System.out.println("Database connection error: " + e.getMessage());
        }
    }

    // Utility methods for date and time validation
    static boolean isValidTime(String time) {
        try {
            String[] parts = time.split(":");
            if (parts.length != 3) return false;

            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            int seconds = Integer.parseInt(parts[2]);

            return hours >= 0 && hours <= 23 &&
                    minutes >= 0 && minutes <= 59 &&
                    seconds >= 0 && seconds <= 59;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return false;
        }
    }

    static boolean isValidDate(String date) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            sdf.setLenient(false);
            sdf.parse(date);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    static boolean isFutureDate(String date) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            sdf.setLenient(false);
            Date inputDate = sdf.parse(date);
            Date currentDate = new Date();

            // Clear time part for accurate comparison
            SimpleDateFormat dateOnly = new SimpleDateFormat("yyyy-MM-dd");
            String inputDateStr = dateOnly.format(inputDate);
            String currentDateStr = dateOnly.format(currentDate);

            return inputDateStr.compareTo(currentDateStr) >= 0;
        } catch (ParseException e) {
            return false;
        }
    }

    // Load seat assignments from database into BSTs
    static void loadSeatAssignments() throws SQLException {
        String sql = "SELECT * FROM seat_assignments";
        ResultSet rs = st.executeQuery(sql);

        while (rs.next()) {
            int flightNumber = rs.getInt("FlightNumber");
            int seatNumber = rs.getInt("SeatNumber");
            String passengerName = rs.getString("PassengerName");

            if (!flightSeatTrees.containsKey(flightNumber)) {
                flightSeatTrees.put(flightNumber, null);
            }

            SeatNode root = flightSeatTrees.get(flightNumber);
            flightSeatTrees.put(flightNumber, insert(root, seatNumber, passengerName));
        }
    }

    // BST operations
    static SeatNode insert(SeatNode root, int seatNo, String name) {
        if (root == null) {
            return new SeatNode(seatNo, name);
        }
        if (seatNo < root.seatNo) {
            root.left = insert(root.left, seatNo, name);
        } else if (seatNo > root.seatNo) {
            root.right = insert(root.right, seatNo, name);
        }
        return root;
    }

    static SeatNode search(SeatNode root, int seatNo) {
        if (root == null || root.seatNo == seatNo) {
            return root;
        }
        if (seatNo < root.seatNo) return search(root.left, seatNo);
        return search(root.right, seatNo);
    }

    static SeatNode findMin(SeatNode root) {
        while (root.left != null) root = root.left;
        return root;
    }

    static SeatNode delete(SeatNode root, int seatNo) {
        if (root == null) return root;

        if (seatNo < root.seatNo) {
            root.left = delete(root.left, seatNo);
        } else if (seatNo > root.seatNo) {
            root.right = delete(root.right, seatNo);
        } else {
            if (root.left == null) return root.right;
            else if (root.right == null) return root.left;

            SeatNode temp = findMin(root.right);
            root.seatNo = temp.seatNo;
            root.passengerName = temp.passengerName;
            root.right = delete(root.right, temp.seatNo);
        }
        return root;
    }

    static void inorder(SeatNode root) {
        if (root != null) {
            inorder(root.left);
            String seatType = getSeatType(root.seatNo);
            System.out.println("Seat " + root.seatNo + " (" + seatType + ") -> " + root.passengerName);
            inorder(root.right);
        }
    }

    static String getSeatType(int seatNumber) {
        int seatInRow = (seatNumber - 1) % 6;
        if (seatInRow == 0 || seatInRow == 5) {
            return "Window";
        } else if (seatInRow == 2 || seatInRow == 3) {
            return "Aisle";
        } else {
            return "Middle";
        }
    }

    static int countNodes(SeatNode root) {
        if (root == null) return 0;
        return 1 + countNodes(root.left) + countNodes(root.right);
    }

    // ---------------- LOGIN METHODS ----------------
    static boolean adminLogin(Scanner sc) {
        System.out.print("Enter Admin Username: ");
        String user = sc.nextLine();
        System.out.print("Enter Admin Password: ");
        String pass = sc.nextLine();

        if (user.equals("Airline") && pass.equals("A123")) {
            System.out.println(" Admin login successful ");
            return true;
        } else {
            System.out.println(" Invalid Admin ");
            return false;
        }
    }

    static boolean passengerLogin(Scanner sc) {
        System.out.print("Enter Passenger Name: ");
        String name = sc.nextLine();
        System.out.print("Enter Passenger Email: ");
        String email = sc.nextLine();

        if (!name.isEmpty() && email.contains("@")) {
            System.out.println(" Passenger login successful ");
            return true;
        } else {
            System.out.println(" Invalid Passenger details ");
            return false;
        }
    }

    // ---------------- ADMIN MENU ----------------
    static void adminMenu(Scanner sc) throws Exception {
        while (true) {
            System.out.println("\n--- Admin Menu ---");
            System.out.println("1. Add Flight");
            System.out.println("2. Update Flight");
            System.out.println("3. Delete Flight");
            System.out.println("4. View All Flights");
            System.out.println("5. View Seat Assignments for a Flight");
            System.out.println("6. Exit..");
            System.out.print("Enter choice: ");
            int choice = -1;
            try {
                choice = sc.nextInt();
            } catch (InputMismatchException e) {
                System.out.println("Invalid input! Please enter a number.");
                sc.nextLine();
                continue;
            }
            sc.nextLine();

            switch (choice) {
                case 1:
                    addFlight(sc);
                    break;
                case 2:
                    updateFlight(sc);
                    break;
                case 3:
                    deleteFlight(sc);
                    break;
                case 4:
                    viewAllFlights();
                    break;
                case 5:
                    viewFlightSeatAssignments(sc);
                    break;
                case 6:
                    return;
                default:
                    System.out.println("Invalid choice!");
            }
        }
    }

    // ---------------- PASSENGER MENU ----------------
    static void passengerMenu(Scanner sc) throws Exception {
        while (true) {
            System.out.println("\n--- Passenger Menu ---");
            System.out.println("1. Book Ticket");
            System.out.println("2. Cancel Booking");
            System.out.println("3. Update Booking");
            System.out.println("4. View Booking Details");
            System.out.println("5. Add Special Requests");
            System.out.println("6. View Special Requests");
            System.out.println("7. View All Flights");
            System.out.println("8. Select Specific Seat");
            System.out.println("9. View Available Seats");
            System.out.println("10. Exit..");
            System.out.print("Enter choice: ");
            int choice = -1;
            try {
                choice = sc.nextInt();
            } catch (InputMismatchException e) {
                System.out.println("Invalid input! Please enter a number.");
                sc.nextLine();
                continue;
            }
            sc.nextLine();

            switch (choice) {
                case 1:
                    bookTicket(sc);
                    break;
                case 2:
                    cancelBooking(sc);
                    break;
                case 3:
                    updateBooking(sc);
                    break;
                case 4:
                    viewBookingDetails(sc);
                    break;
                case 5:
                    addSpecialRequests(sc);
                    break;
                case 6:
                    viewSpecialRequests(sc);
                    break;
                case 7:
                    viewAllFlights();
                    break;
                case 8:
                    selectSpecificSeat(sc);
                    break;
                case 9:
                    viewAvailableSeats(sc);
                    break;
                case 10:
                    return;
                default:
                    System.out.println("Invalid choice!");
            }
        }
    }

    // ---------------- FLIGHT METHODS ----------------
    static void addFlight(Scanner sc) throws SQLException {
        System.out.print("Enter Source: ");
        String source = sc.nextLine();
        System.out.print("Enter Destination: ");
        String destination = sc.nextLine();

        String departureTime;
        while (true) {
            System.out.print("Enter Departure Time (HH:MM:SS): ");
            departureTime = sc.nextLine();
            if (isValidTime(departureTime)) break;
            System.out.println("Invalid time format! Use HH:MM:SS (e.g., 14:30:00)");
        }

        String arrivalTime;
        while (true) {
            System.out.print("Enter Arrival Time (HH:MM:SS): ");
            arrivalTime = sc.nextLine();
            if (isValidTime(arrivalTime)) break;
            System.out.println("Invalid time format! Use HH:MM:SS (e.g., 16:45:00)");
        }

        String flightDate;
        while (true) {
            System.out.print("Enter Flight Date (YYYY-MM-DD): ");
            flightDate = sc.nextLine();
            if (isValidDate(flightDate) && isFutureDate(flightDate)) break;
            System.out.println("Invalid date! Use YYYY-MM-DD format and future date");
        }

        int availableSeats = 0;
        while (true) {
            try {
                System.out.print("Enter Available Seats: ");
                availableSeats = sc.nextInt();
                if (availableSeats > 0) break;
                System.out.println("Enter positive number of seats");
            } catch (InputMismatchException e) {
                System.out.println("Invalid input! Enter a number");
                sc.nextLine();
            }
        }

        double price = 0;
        while (true) {
            try {
                System.out.print("Enter Flight Price: ");
                price = sc.nextDouble();
                if (price > 0) break;
                System.out.println("Enter positive price");
            } catch (InputMismatchException e) {
                System.out.println("Invalid input! Enter a number");
                sc.nextLine();
            }
        }
        sc.nextLine();

        try {
            String insertSQL = "INSERT INTO Flights (Source, Destination, DepartureTime, ArrivalTime, FlightDate, AvailableSeats, Price) VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement pstmt = con.prepareStatement(insertSQL);
            pstmt.setString(1, source);
            pstmt.setString(2, destination);
            pstmt.setString(3, departureTime);
            pstmt.setString(4, arrivalTime);
            pstmt.setString(5, flightDate);
            pstmt.setInt(6, availableSeats);
            pstmt.setDouble(7, price);
            pstmt.executeUpdate();

            ResultSet rs = st.executeQuery("SELECT LAST_INSERT_ID()");
            if (rs.next()) {
                int flightNumber = rs.getInt(1);
                flightSeatTrees.put(flightNumber, null);
            }
            System.out.println("Flight added successfully.");
        } catch (SQLException e) {
            System.out.println("Error adding flight: " + e.getMessage());
        }
    }

    static void updateFlight(Scanner sc) throws SQLException {
        System.out.print("Enter Flight Number to update: ");
        int flightNumber = sc.nextInt();
        sc.nextLine();

        System.out.print("Enter new Source: ");
        String source = sc.nextLine();
        System.out.print("Enter new Destination: ");
        String destination = sc.nextLine();

        String departureTime;
        while (true) {
            System.out.print("Enter new Departure Time (HH:MM:SS): ");
            departureTime = sc.nextLine();
            if (isValidTime(departureTime)) break;
            System.out.println("Invalid time format! Use HH:MM:SS");
        }

        String arrivalTime;
        while (true) {
            System.out.print("Enter new Arrival Time (HH:MM:SS): ");
            arrivalTime = sc.nextLine();
            if (isValidTime(arrivalTime)) break;
            System.out.println("Invalid time format! Use HH:MM:SS");
        }

        String flightDate;
        while (true) {
            System.out.print("Enter new Flight Date (YYYY-MM-DD): ");
            flightDate = sc.nextLine();
            if (isValidDate(flightDate) && isFutureDate(flightDate)) break;
            System.out.println("Invalid date! Use YYYY-MM-DD format and future date");
        }

        int availableSeats = 0;
        while (true) {
            try {
                System.out.print("Enter new Available Seats: ");
                availableSeats = sc.nextInt();
                if (availableSeats >= 0) break;
                System.out.println("Enter non-negative number of seats");
            } catch (InputMismatchException e) {
                System.out.println("Invalid input! Enter a number");
                sc.nextLine();
            }
        }

        double price = 0;
        while (true) {
            try {
                System.out.print("Enter new Price: ");
                price = sc.nextDouble();
                if (price >= 0) break;
                System.out.println("Enter non-negative price");
            } catch (InputMismatchException e) {
                System.out.println("Invalid input! Enter a number");
                sc.nextLine();
            }
        }
        sc.nextLine();

        try {
            String updateSQL = "UPDATE Flights SET Source = ?, Destination = ?, DepartureTime = ?, ArrivalTime = ?, FlightDate = ?, AvailableSeats = ?, Price = ? WHERE FlightNumber = ?";
            PreparedStatement pstmt = con.prepareStatement(updateSQL);
            pstmt.setString(1, source);
            pstmt.setString(2, destination);
            pstmt.setString(3, departureTime);
            pstmt.setString(4, arrivalTime);
            pstmt.setString(5, flightDate);
            pstmt.setInt(6, availableSeats);
            pstmt.setDouble(7, price);
            pstmt.setInt(8, flightNumber);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Flight updated successfully.");
            } else {
                System.out.println("Flight not found. Update failed.");
            }
        } catch (SQLException e) {
            System.out.println("Error updating flight: " + e.getMessage());
        }
    }

    static void deleteFlight(Scanner sc) throws SQLException {
        System.out.print("Enter Flight Number to delete: ");
        int flightNumber = sc.nextInt();

        try {
            String deleteSeatsSQL = "DELETE FROM seat_assignments WHERE FlightNumber = ?";
            PreparedStatement pstmt = con.prepareStatement(deleteSeatsSQL);
            pstmt.setInt(1, flightNumber);
            pstmt.executeUpdate();

            String deleteBookingsSQL = "DELETE FROM bookings WHERE FlightNumber = ?";
            pstmt = con.prepareStatement(deleteBookingsSQL);
            pstmt.setInt(1, flightNumber);
            pstmt.executeUpdate();

            String deleteFlightSQL = "DELETE FROM Flights WHERE FlightNumber = ?";
            pstmt = con.prepareStatement(deleteFlightSQL);
            pstmt.setInt(1, flightNumber);
            pstmt.executeUpdate();

            flightSeatTrees.remove(flightNumber);
            System.out.println("Flight deleted successfully.");
        } catch (SQLException e) {
            System.out.println("Error deleting flight: " + e.getMessage());
        }
    }

    static void viewAllFlights() throws SQLException {
        try {
            String selectSQL = "SELECT * FROM Flights";
            PreparedStatement pstmt = con.prepareStatement(selectSQL);
            ResultSet rs = pstmt.executeQuery();

            System.out.println("\n--- List of All Flights ---");
            System.out.printf("%-15s %-15s %-15s %-15s %-15s %-15s %-15s %-10s%n",
                    "Flight Number", "Source", "Destination", "Departure", "Arrival", "Date", "Available Seats", "Price");
            System.out.println("----------------------------------------------------------------------------------------------------------------------");

            boolean hasFlights = false;
            while (rs.next()) {
                hasFlights = true;
                int flightNumber = rs.getInt("FlightNumber");
                String source = rs.getString("Source");
                String destination = rs.getString("Destination");
                Time departureTime = rs.getTime("DepartureTime");
                Time arrivalTime = rs.getTime("ArrivalTime");
                String flightDate = String.valueOf(rs.getDate("FlightDate"));
                int seats = rs.getInt("AvailableSeats");
                double price = rs.getDouble("Price");

                System.out.printf("%-15d %-15s %-15s %-15s %-15s %-15s %-15d %-10.2f%n",
                        flightNumber, source, destination, departureTime, arrivalTime, flightDate, seats, price);
            }

            if (!hasFlights) {
                System.out.println("No flights found.");
            }
        } catch (SQLException e) {
            System.out.println("Error viewing flights: " + e.getMessage());
        }
    }

    static void viewFlightSeatAssignments(Scanner sc) throws SQLException {
        System.out.print("Enter Flight Number: ");
        int flightNumber = sc.nextInt();

        if (!flightSeatTrees.containsKey(flightNumber)) {
            System.out.println("Flight not found or no seat assignments.");
            return;
        }

        System.out.println("\n--- Seat Assignments for Flight " + flightNumber + " ---");
        SeatNode root = flightSeatTrees.get(flightNumber);
        if (root == null) {
            System.out.println("No seats booked yet.");
        } else {
            inorder(root);
        }
    }

    static void viewAvailableSeats(Scanner sc) throws SQLException {
        System.out.print("Enter Flight Number: ");
        int flightNumber = sc.nextInt();

        try {
            String sql = "SELECT AvailableSeats FROM Flights WHERE FlightNumber = ?";
            PreparedStatement pstmt = con.prepareStatement(sql);
            pstmt.setInt(1, flightNumber);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                int totalSeats = rs.getInt("AvailableSeats");
                int bookedSeats = 0;
                if (flightSeatTrees.containsKey(flightNumber)) {
                    bookedSeats = countNodes(flightSeatTrees.get(flightNumber));
                }

                System.out.println("\n--- Available Seats for Flight " + flightNumber + " ---");
                System.out.println("Total seats: " + totalSeats);
                System.out.println("Booked seats: " + bookedSeats);
                System.out.println("Available seats: " + (totalSeats - bookedSeats));

                System.out.println("Available seat numbers (with types):");
                for (int i = 1; i <= totalSeats; i++) {
                    if (!flightSeatTrees.containsKey(flightNumber) ||
                            search(flightSeatTrees.get(flightNumber), i) == null) {
                        String seatType = getSeatType(i);
                        System.out.println("Seat " + i + " (" + seatType + ")");
                    }
                }
            } else {
                System.out.println("Flight not found.");
            }
        } catch (SQLException e) {
            System.out.println("Error viewing available seats: " + e.getMessage());
        }
    }

    // ---------------- BOOKING METHODS ----------------
    static void bookTicket(Scanner sc) throws SQLException {
        System.out.print("Enter Flight Number to book: ");
        int flightNumber = sc.nextInt();
        sc.nextLine();

        // Check if flight exists and date is valid
        try {
            String checkFlightSQL = "SELECT FlightDate FROM Flights WHERE FlightNumber = ?";
            PreparedStatement checkStmt = con.prepareStatement(checkFlightSQL);
            checkStmt.setInt(1, flightNumber);
            ResultSet flightRS = checkStmt.executeQuery();

            if (!flightRS.next()) {
                System.out.println("Flight not found");
                return;
            }

            // Check if flight date is in the past
            String flightDateStr = flightRS.getString("FlightDate");
            if (!isFutureDate(flightDateStr)) {
                System.out.println("Cannot book tickets for past flights");
                return;
            }
        } catch (SQLException e) {
            System.out.println("Error checking flight: " + e.getMessage());
            return;
        }

        System.out.print("Enter Passenger Name: ");
        String passengerName = sc.nextLine();
        System.out.print("Enter Passenger Email: ");
        String passengerEmail = sc.nextLine();

        int seats = 0;
        while (true) {
            try {
                System.out.print("Enter Number of Seats to book: ");
                seats = sc.nextInt();
                if (seats > 0) break;
                System.out.println("Enter positive number of seats");
            } catch (InputMismatchException e) {
                System.out.println("Invalid input! Enter a number");
                sc.nextLine();
            }
        }

        try {
            String checkSeatsSQL = "SELECT AvailableSeats FROM Flights WHERE FlightNumber = ?";
            PreparedStatement pstmt = con.prepareStatement(checkSeatsSQL);
            pstmt.setInt(1, flightNumber);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                int availableSeats = rs.getInt("AvailableSeats");
                if (availableSeats >= seats) {
                    String insertBookingSQL = "INSERT INTO bookings (FlightNumber, PassengerName, PassengerEmail, SeatsBooked, BookingDate) VALUES (?, ?, ?, ?, CURDATE())";
                    pstmt = con.prepareStatement(insertBookingSQL);
                    pstmt.setInt(1, flightNumber);
                    pstmt.setString(2, passengerName);
                    pstmt.setString(3, passengerEmail);
                    pstmt.setInt(4, seats);
                    pstmt.executeUpdate();

                    String updateSeatsSQL = "UPDATE Flights SET AvailableSeats = AvailableSeats - ? WHERE FlightNumber = ?";
                    pstmt = con.prepareStatement(updateSeatsSQL);
                    pstmt.setInt(1, seats);
                    pstmt.setInt(2, flightNumber);
                    pstmt.executeUpdate();

                    // Auto-assign seats
                    int seatsAssigned = 0;
                    int seatNumber = 1;

                    if (!flightSeatTrees.containsKey(flightNumber)) {
                        flightSeatTrees.put(flightNumber, null);
                    }

                    SeatNode root = flightSeatTrees.get(flightNumber);

                    while (seatsAssigned < seats) {
                        if (search(root, seatNumber) == null) {
                            root = insert(root, seatNumber, passengerName);
                            seatsAssigned++;

                            String insertSeatSQL = "INSERT INTO seat_assignments (FlightNumber, SeatNumber, PassengerName) VALUES (?, ?, ?)";
                            PreparedStatement seatStmt = con.prepareStatement(insertSeatSQL);
                            seatStmt.setInt(1, flightNumber);
                            seatStmt.setInt(2, seatNumber);
                            seatStmt.setString(3, passengerName);
                            seatStmt.executeUpdate();
                        }
                        seatNumber++;
                    }

                    flightSeatTrees.put(flightNumber, root);
                    System.out.println("Ticket booked successfully with auto-assigned seats");
                } else {
                    System.out.println("Not enough seats available");
                }
            } else {
                System.out.println("Flight not found");
            }
        } catch (SQLException e) {
            System.out.println("Error booking ticket: " + e.getMessage());
        }
    }

    static void selectSpecificSeat(Scanner sc) throws SQLException {
        System.out.print("Enter Flight Number: ");
        int flightNumber = sc.nextInt();
        sc.nextLine();

        // Check if flight exists and date is valid
        try {
            String checkFlightSQL = "SELECT FlightDate, AvailableSeats FROM Flights WHERE FlightNumber = ?";
            PreparedStatement checkStmt = con.prepareStatement(checkFlightSQL);
            checkStmt.setInt(1, flightNumber);
            ResultSet flightRS = checkStmt.executeQuery();

            if (!flightRS.next()) {
                System.out.println("Flight not found");
                return;
            }

            // Check if flight date is in the past
            String flightDateStr = flightRS.getString("FlightDate");
            if (!isFutureDate(flightDateStr)) {
                System.out.println("Cannot select seats for past flights");
                return;
            }
        } catch (SQLException e) {
            System.out.println("Error checking flight: " + e.getMessage());
            return;
        }

        System.out.print("Enter Passenger Name: ");
        String passengerName = sc.nextLine();
        System.out.print("Enter Passenger Email: ");
        String passengerEmail = sc.nextLine();

        viewAvailableSeatsForSelection(flightNumber);

        int seatNumber = 0;
        while (true) {
            try {
                System.out.print("Enter Seat Number: ");
                seatNumber = sc.nextInt();
                if (seatNumber > 0) break;
                System.out.println("Enter positive seat number");
            } catch (InputMismatchException e) {
                System.out.println("Invalid input! Enter a number");
                sc.nextLine();
            }
        }

        try {
            String checkFlightSQL = "SELECT AvailableSeats FROM Flights WHERE FlightNumber = ?";
            PreparedStatement pstmt = con.prepareStatement(checkFlightSQL);
            pstmt.setInt(1, flightNumber);
            ResultSet rs = pstmt.executeQuery();

            if (!rs.next()) {
                System.out.println("Flight not found.");
                return;
            }

            int availableSeats = rs.getInt("AvailableSeats");
            if (seatNumber < 1 || seatNumber > availableSeats) {
                System.out.println("Invalid seat number. Please select between 1 and " + availableSeats);
                return;
            }

            if (!flightSeatTrees.containsKey(flightNumber)) {
                flightSeatTrees.put(flightNumber, null);
            }

            SeatNode root = flightSeatTrees.get(flightNumber);
            if (search(root, seatNumber) != null) {
                System.out.println("Seat already booked. Please choose another seat.");
                return;
            }

            root = insert(root, seatNumber, passengerName);
            flightSeatTrees.put(flightNumber, root);

            String insertSeatSQL = "INSERT INTO seat_assignments (FlightNumber, SeatNumber, PassengerName) VALUES (?, ?, ?)";
            pstmt = con.prepareStatement(insertSeatSQL);
            pstmt.setInt(1, flightNumber);
            pstmt.setInt(2, seatNumber);
            pstmt.setString(3, passengerName);
            pstmt.executeUpdate();

            String insertBookingSQL = "INSERT INTO bookings (FlightNumber, PassengerName, PassengerEmail, SeatsBooked, BookingDate) VALUES (?, ?, ?, 1, CURDATE())";
            pstmt = con.prepareStatement(insertBookingSQL);
            pstmt.setInt(1, flightNumber);
            pstmt.setString(2, passengerName);
            pstmt.setString(3, passengerEmail);
            pstmt.executeUpdate();

            String updateSeatsSQL = "UPDATE Flights SET AvailableSeats = AvailableSeats - 1 WHERE FlightNumber = ?";
            pstmt = con.prepareStatement(updateSeatsSQL);
            pstmt.setInt(1, flightNumber);
            pstmt.executeUpdate();

            String seatType = getSeatType(seatNumber);
            System.out.println("Seat " + seatNumber + " (" + seatType + ") booked successfully for " + passengerName);
        } catch (SQLException e) {
            System.out.println("Error selecting seat: " + e.getMessage());
        }
    }

    static void viewAvailableSeatsForSelection(int flightNumber) throws SQLException {
        try {
            String sql = "SELECT AvailableSeats FROM Flights WHERE FlightNumber = ?";
            PreparedStatement pstmt = con.prepareStatement(sql);
            pstmt.setInt(1, flightNumber);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                int totalSeats = rs.getInt("AvailableSeats");
                System.out.println("\n--- Available Seats for Flight " + flightNumber + " ---");
                for (int i = 1; i <= totalSeats; i++) {
                    if (!flightSeatTrees.containsKey(flightNumber) ||
                            search(flightSeatTrees.get(flightNumber), i) == null) {
                        String seatType = getSeatType(i);
                        System.out.println("Seat " + i + " (" + seatType + ")");
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error viewing available seats: " + e.getMessage());
        }
    }

    static void cancelBooking(Scanner sc) throws SQLException {
        System.out.print("Enter Booking ID to cancel: ");
        int bookingId = sc.nextInt();

        try {
            String selectBookingSQL = "SELECT FlightNumber, SeatsBooked, PassengerName FROM bookings WHERE BookingID = ?";
            PreparedStatement pstmt = con.prepareStatement(selectBookingSQL);
            pstmt.setInt(1, bookingId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                int flightNumber = rs.getInt("FlightNumber");
                int seatsBooked = rs.getInt("SeatsBooked");
                String passengerName = rs.getString("PassengerName");

                String deleteBookingSQL = "DELETE FROM bookings WHERE BookingID = ?";
                pstmt = con.prepareStatement(deleteBookingSQL);
                pstmt.setInt(1, bookingId);
                pstmt.executeUpdate();

                String updateSeatsSQL = "UPDATE Flights SET AvailableSeats = AvailableSeats + ? WHERE FlightNumber = ?";
                pstmt = con.prepareStatement(updateSeatsSQL);
                pstmt.setInt(1, seatsBooked);
                pstmt.setInt(2, flightNumber);
                pstmt.executeUpdate();

                if (flightSeatTrees.containsKey(flightNumber)) {
                    SeatNode root = flightSeatTrees.get(flightNumber);
                    String selectSeatsSQL = "SELECT SeatNumber FROM seat_assignments WHERE FlightNumber = ? AND PassengerName = ?";
                    pstmt = con.prepareStatement(selectSeatsSQL);
                    pstmt.setInt(1, flightNumber);
                    pstmt.setString(2, passengerName);
                    ResultSet seatsRS = pstmt.executeQuery();

                    while (seatsRS.next()) {
                        int seatNumber = seatsRS.getInt("SeatNumber");
                        root = delete(root, seatNumber);

                        String deleteSeatSQL = "DELETE FROM seat_assignments WHERE FlightNumber = ? AND SeatNumber = ?";
                        PreparedStatement deleteStmt = con.prepareStatement(deleteSeatSQL);
                        deleteStmt.setInt(1, flightNumber);
                        deleteStmt.setInt(2, seatNumber);
                        deleteStmt.executeUpdate();
                    }
                    flightSeatTrees.put(flightNumber, root);
                }
                System.out.println("Booking canceled successfully");
            } else {
                System.out.println("Booking not found");
            }
        } catch (SQLException e) {
            System.out.println("Error canceling booking: " + e.getMessage());
        }
    }

    static void updateBooking(Scanner sc) throws SQLException {
        System.out.print("Enter Booking ID to update: ");
        int bookingId = sc.nextInt();
        sc.nextLine();
        System.out.print("Enter new Passenger Name: ");
        String passengerName = sc.nextLine();
        System.out.print("Enter new Passenger Email: ");
        String passengerEmail = sc.nextLine();

        int newSeats = 0;
        while (true) {
            try {
                System.out.print("Enter new number of seats: ");
                newSeats = sc.nextInt();
                if (newSeats >= 0) break;
                System.out.println("Enter non-negative number of seats");
            } catch (InputMismatchException e) {
                System.out.println("Invalid input! Enter a number");
                sc.nextLine();
            }
        }

        try {
            String selectBookingSQL = "SELECT FlightNumber, SeatsBooked, PassengerName FROM bookings WHERE BookingID = ?";
            PreparedStatement pstmt = con.prepareStatement(selectBookingSQL);
            pstmt.setInt(1, bookingId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                int flightNumber = rs.getInt("FlightNumber");
                int oldSeats = rs.getInt("SeatsBooked");
                String oldPassengerName = rs.getString("PassengerName");

                String updateBookingSQL = "UPDATE bookings SET PassengerName = ?, PassengerEmail = ?, SeatsBooked = ? WHERE BookingID = ?";
                pstmt = con.prepareStatement(updateBookingSQL);
                pstmt.setString(1, passengerName);
                pstmt.setString(2, passengerEmail);
                pstmt.setInt(3, newSeats);
                pstmt.setInt(4, bookingId);
                pstmt.executeUpdate();

                String updateSeatsSQL = "UPDATE Flights SET AvailableSeats = AvailableSeats + ? - ? WHERE FlightNumber = ?";
                pstmt = con.prepareStatement(updateSeatsSQL);
                pstmt.setInt(1, oldSeats);
                pstmt.setInt(2, newSeats);
                pstmt.setInt(3, flightNumber);
                pstmt.executeUpdate();

                if (!oldPassengerName.equals(passengerName) && flightSeatTrees.containsKey(flightNumber)) {
                    String updateSeatNameSQL = "UPDATE seat_assignments SET PassengerName = ? WHERE FlightNumber = ? AND PassengerName = ?";
                    pstmt = con.prepareStatement(updateSeatNameSQL);
                    pstmt.setString(1, passengerName);
                    pstmt.setInt(2, flightNumber);
                    pstmt.setString(3, oldPassengerName);
                    pstmt.executeUpdate();
                    rebuildFlightBST(flightNumber);
                }
                System.out.println("Booking updated successfully.");
            } else {
                System.out.println("Booking not found.");
            }
        } catch (SQLException e) {
            System.out.println("Error updating booking: " + e.getMessage());
        }
    }

    static void rebuildFlightBST(int flightNumber) throws SQLException {
        try {
            String sql = "SELECT SeatNumber, PassengerName FROM seat_assignments WHERE FlightNumber = ?";
            PreparedStatement pstmt = con.prepareStatement(sql);
            pstmt.setInt(1, flightNumber);
            ResultSet rs = pstmt.executeQuery();

            SeatNode root = null;
            while (rs.next()) {
                int seatNumber = rs.getInt("SeatNumber");
                String passengerName = rs.getString("PassengerName");
                root = insert(root, seatNumber, passengerName);
            }
            flightSeatTrees.put(flightNumber, root);
        } catch (SQLException e) {
            System.out.println("Error rebuilding BST: " + e.getMessage());
        }
    }

    static void viewBookingDetails(Scanner sc) throws SQLException {
        System.out.print("Enter Booking ID to view details: ");
        int bookingId = sc.nextInt();

        try {
            String selectBookingSQL = "SELECT * FROM bookings WHERE BookingID = ?";
            PreparedStatement pstmt = con.prepareStatement(selectBookingSQL);
            pstmt.setInt(1, bookingId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                System.out.println("Booking ID: " + rs.getInt("BookingID"));
                System.out.println("Flight Number: " + rs.getInt("FlightNumber"));
                System.out.println("Passenger Name: " + rs.getString("PassengerName"));
                System.out.println("Passenger Email: " + rs.getString("PassengerEmail"));
                System.out.println("Seats Booked: " + rs.getInt("SeatsBooked"));
                System.out.println("Booking Date: " + rs.getDate("BookingDate"));

                int flightNumber = rs.getInt("FlightNumber");
                String passengerName = rs.getString("PassengerName");

                String seatSQL = "SELECT SeatNumber FROM seat_assignments WHERE FlightNumber = ? AND PassengerName = ?";
                PreparedStatement seatStmt = con.prepareStatement(seatSQL);
                seatStmt.setInt(1, flightNumber);
                seatStmt.setString(2, passengerName);
                ResultSet seatRS = seatStmt.executeQuery();

                System.out.print("Assigned Seats: ");
                boolean hasSeats = false;
                while (seatRS.next()) {
                    hasSeats = true;
                    int seatNumber = seatRS.getInt("SeatNumber");
                    String seatType = getSeatType(seatNumber);
                    System.out.print(seatNumber + " (" + seatType + ") ");
                }
                if (!hasSeats) System.out.print("Not yet assigned");
                System.out.println();
            } else {
                System.out.println("Booking not found.");
            }
        } catch (SQLException e) {
            System.out.println("Error viewing booking details: " + e.getMessage());
        }
    }

    static void addSpecialRequests(Scanner sc) throws SQLException {
        System.out.print("Enter Booking ID: ");
        int bookingId = sc.nextInt();
        sc.nextLine();

        System.out.print("Enter special requests: ");
        String specialRequests = sc.nextLine();

        try {
            String updateSQL = "UPDATE bookings SET SpecialRequests = ? WHERE BookingID = ?";
            PreparedStatement pstmt = con.prepareStatement(updateSQL);
            pstmt.setString(1, specialRequests);
            pstmt.setInt(2, bookingId);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Special requests added successfully");
            } else {
                System.out.println("Booking not found");
            }
        } catch (SQLException e) {
            System.out.println("Error adding special requests: " + e.getMessage());
        }
    }

    static void viewSpecialRequests(Scanner sc) throws SQLException {
        System.out.print("Enter Booking ID: ");
        int bookingId = sc.nextInt();

        try {
            String selectSQL = "SELECT SpecialRequests FROM bookings WHERE BookingID = ?";
            PreparedStatement pstmt = con.prepareStatement(selectSQL);
            pstmt.setInt(1, bookingId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String specialRequests = rs.getString("SpecialRequests");
                if (specialRequests != null && !specialRequests.trim().isEmpty()) {
                    System.out.println("Special Requests: " + specialRequests);
                } else {
                    System.out.println("No special requests found for this booking.");
                }
            } else {
                System.out.println("Booking not found.");
            }
        } catch (SQLException e) {
            System.out.println("Error viewing special requests: " + e.getMessage());
        }
    }
}

// Node for the BST
class SeatNode {
    int seatNo;
    String passengerName;
    SeatNode left, right;

    SeatNode(int seatNo, String passengerName) {
        this.seatNo = seatNo;
        this.passengerName = passengerName;
        left = right = null;
    }
}