package iti.tel.twilio_project;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.*;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

/**
 * Handles new Customer registration.
 * Flow: Insert user → Generate OTP → Send OTP via customer's own Twilio account → Redirect to verify page.
 */
public class RegisterServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // 1. Collect form data
        String name     = request.getParameter("name");
        String birthday = request.getParameter("birthday");
        String email    = request.getParameter("email");
        String phone    = request.getParameter("phone");       // customer's MSISDN e.g. +201012345678
        String job      = request.getParameter("job");
        String address  = request.getParameter("address");
        String password = request.getParameter("password");
        String sid      = request.getParameter("twilio_sid");
        String token    = request.getParameter("twilio_token");
        String sender   = request.getParameter("sender_id");   // Twilio From number / sender ID

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();

            // 2. Check if email already exists
            PreparedStatement checkPs = conn.prepareStatement(
                "SELECT id FROM users WHERE email = ?");
            checkPs.setString(1, email);
            ResultSet checkRs = checkPs.executeQuery();
            if (checkRs.next()) {
                response.sendRedirect("RegisterPage.html?error=email_exists");
                return;
            }

            // 3. Insert user as UNVERIFIED CUSTOMER
            String insertSql =
                "INSERT INTO users " +
                "(name, email, password, phone_number_msisdn, birthday, job, address, " +
                " twilio_sid, twilio_token, allowed_sender_id, role, is_verified) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'CUSTOMER', false)";

            PreparedStatement ps = conn.prepareStatement(insertSql);
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, password);          // NOTE: hash with BCrypt in production
            ps.setString(4, phone);
            ps.setDate(5,   Date.valueOf(birthday));
            ps.setString(6, job);
            ps.setString(7, address);
            ps.setString(8, sid);
            ps.setString(9, token);
            ps.setString(10, sender);
            ps.executeUpdate();

            // 4. Retrieve the newly created user id
            PreparedStatement ps2 = conn.prepareStatement(
                "SELECT id FROM users WHERE email = ?");
            ps2.setString(1, email);
            ResultSet rs = ps2.executeQuery();

            int userId = 0;
            if (rs.next()) {
                userId = rs.getInt("id");
            }

            // 5. Generate 6-digit OTP
            int otp = (int)(Math.random() * 900000) + 100000;
            String otpStr = String.valueOf(otp);

            // 6. Save OTP to verification table (expires in 5 minutes)
            String vSql =
                "INSERT INTO verification (user_id, code, expires_at, is_used) " +
                "VALUES (?, ?, NOW() + INTERVAL '5 minutes', false)";
            PreparedStatement ps3 = conn.prepareStatement(vSql);
            ps3.setInt(1, userId);
            ps3.setString(2, otpStr);
            ps3.executeUpdate();

            // 7. Send OTP via customer's own Twilio account
            try {
                Twilio.init(sid, token);
                Message.creator(
                    new PhoneNumber(phone),          // To: customer's phone
                    new PhoneNumber(sender),         // From: customer's Twilio sender ID
                    "Your SMS Portal verification code is: " + otpStr +
                    ". It expires in 5 minutes."
                ).create();
                System.out.println("OTP sent via Twilio to " + phone);
            } catch (Exception twilioEx) {
                // Log but don't crash — user can still verify manually in testing
                System.err.println("Twilio SMS failed: " + twilioEx.getMessage());
                System.out.println("FALLBACK OTP (for testing) = " + otpStr);
            }

            // 8. Store email in session so VerifyServlet can tie the OTP back to the user
            request.getSession().setAttribute("pending_email", email);

            // 9. Redirect to verify page
            response.sendRedirect("verify.html");

        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect("RegisterPage.html?error=server_error");
        } finally {
            try { if (conn != null) conn.close(); } catch (Exception ignored) {}
        }
    }
}
