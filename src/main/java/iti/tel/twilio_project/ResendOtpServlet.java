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
 * Resends a fresh OTP to the customer's phone.
 * POST /ResendOtpServlet  (body param: email)
 */
public class ResendOtpServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String email = request.getParameter("email");

        if (email == null || email.isBlank()) {
            response.sendRedirect("verify.html?error=missing_email");
            return;
        }

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();

            // Look up the user by email (must be unverified customer)
            PreparedStatement ps = conn.prepareStatement(
                "SELECT id, phone_number_msisdn, twilio_sid, twilio_token, allowed_sender_id " +
                "FROM users WHERE email = ? AND is_verified = false AND role = 'CUSTOMER'"
            );
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                // Either already verified or doesn't exist
                response.sendRedirect("verify.html?error=account_not_found");
                return;
            }

            int    userId = rs.getInt("id");
            String phone  = rs.getString("phone_number_msisdn");
            String sid    = rs.getString("twilio_sid");
            String token  = rs.getString("twilio_token");
            String sender = rs.getString("allowed_sender_id");

            // Invalidate all previous unused OTPs for this user
            PreparedStatement invalidate = conn.prepareStatement(
                "UPDATE verification SET is_used = true WHERE user_id = ? AND is_used = false"
            );
            invalidate.setInt(1, userId);
            invalidate.executeUpdate();

            // Generate new OTP
            int otp = (int)(Math.random() * 900000) + 100000;
            String otpStr = String.valueOf(otp);

            // Save new OTP
            PreparedStatement ps2 = conn.prepareStatement(
                "INSERT INTO verification (user_id, code, expires_at, is_used) " +
                "VALUES (?, ?, NOW() + INTERVAL '5 minutes', false)"
            );
            ps2.setInt(1, userId);
            ps2.setString(2, otpStr);
            ps2.executeUpdate();

            // Send via Twilio
            try {
                Twilio.init(sid, token);
                Message.creator(
                    new PhoneNumber(phone),
                    new PhoneNumber(sender),
                    "Your SMS Portal verification code is: " + otpStr + ". It expires in 5 minutes."
                ).create();
            } catch (Exception twilioEx) {
                System.err.println("Twilio resend failed: " + twilioEx.getMessage());
                System.out.println("FALLBACK OTP = " + otpStr);
            }

            response.sendRedirect("verify.html?resent=true");

        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect("verify.html?error=server_error");
        } finally {
            try { if (conn != null) conn.close(); } catch (Exception ignored) {}
        }
    }
}
