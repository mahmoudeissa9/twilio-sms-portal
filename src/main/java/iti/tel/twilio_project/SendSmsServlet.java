package iti.tel.twilio_project;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.*;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

/**
 * Sends an SMS using the logged-in customer's Twilio credentials.
 * Saves the sent message to sms_messages table.
 */
public class SendSmsServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            response.sendRedirect("login.html");
            return;
        }

        // Only customers can send SMS
        String role = (String) session.getAttribute("user_role");
        if (!"CUSTOMER".equals(role)) {
            response.sendRedirect("admin_home.html");
            return;
        }

        int    userId  = (Integer) session.getAttribute("user_id");
        String sid     = (String)  session.getAttribute("twilio_sid");
        String token   = (String)  session.getAttribute("twilio_token");

        String from    = request.getParameter("from");
        String to      = request.getParameter("to");
        String body    = request.getParameter("body");

        // Basic validation
        if (from == null || from.isBlank() || to == null || to.isBlank() || body == null || body.isBlank()) {
            response.sendRedirect("customer_home.html?error=missing_fields");
            return;
        }

        Connection conn = null;
        try {
            // Send via Twilio
            Twilio.init(sid, token);
            Message msg = Message.creator(
                new PhoneNumber(to),
                new PhoneNumber(from),
                body
            ).create();

            System.out.println("SMS sent. SID: " + msg.getSid());

            // Save to database
            conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO sms_messages (user_id, sender_from, receiver_to, body, direction) " +
                "VALUES (?, ?, ?, ?, 'OUTBOUND')"
            );
            ps.setInt(1, userId);
            ps.setString(2, from);
            ps.setString(3, to);
            ps.setString(4, body);
            ps.executeUpdate();

            response.sendRedirect("customer_home.html?success=sms_sent");

        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect("customer_home.html?error=sms_failed&msg=" +
                java.net.URLEncoder.encode(e.getMessage(), "UTF-8"));
        } finally {
            try { if (conn != null) conn.close(); } catch (Exception ignored) {}
        }
    }
}
