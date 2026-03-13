package com.elite4.anandan.registrationservices.service;


import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.MatchType;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PhoneService {

    private final PhoneNumberUtil pnu = PhoneNumberUtil.getInstance();

    @Value("${app.phone.default-region:IN}")
    private String defaultRegion;

    /** Returns E.164 (e.g., +919876543210) or null if invalid. */
    public String toE164(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            PhoneNumber parsed = pnu.parse(raw, defaultRegion);
            if (!pnu.isValidNumber(parsed)) return null;
            return pnu.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            return null;
        }
    }

    /** Compare two numbers using libphonenumber semantics. */
    public boolean isSameNumber(String a, String b) {
        String e164a = toE164(a);
        String e164b = toE164(b);
        if (e164a != null && e164b != null) return e164a.equals(e164b);

        // Fallback: partial match if one didn't normalize? (business choice)
        // You can also use isNumberMatch if you parse both inputs to PhoneNumber first.
        try {
            PhoneNumber pa = pnu.parse(a, defaultRegion);
            PhoneNumber pb = pnu.parse(b, defaultRegion);
            MatchType mt = pnu.isNumberMatch(pa, pb);
            return mt == MatchType.EXACT_MATCH || mt == MatchType.NSN_MATCH;
        } catch (Exception ignored) {
            return false;
        }
    }
}

