package iti.tel.twilio_project;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Provides a JDBC connection to the Neon PostgreSQL cloud database.
 */
public class DBConnection {

    private static final String URL =
        "jdbc:postgresql://ep-long-glitter-aqjjx7sh-pooler.c-8.us-east-1.aws.neon.tech/neondb" +
        "?sslmode=require&channelBinding=require";

    private static final String USER     = "neondb_owner";
    private static final String PASSWORD = "npg_Rsg2j3kVCEnH";

    public static Connection getConnection() throws Exception {
        Class.forName("org.postgresql.Driver");
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
