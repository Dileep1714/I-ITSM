package com.iitsm.backend.service;

import com.iitsm.backend.api.dto.IncidentDtos.AttachmentView;
import com.iitsm.backend.api.dto.IncidentDtos.PersonRef;
import com.iitsm.backend.common.exception.BusinessRuleException;
import com.iitsm.backend.common.exception.InvalidInputException;
import com.iitsm.backend.common.exception.NotFoundException;
import com.iitsm.backend.domain.incident.Attachment;
import com.iitsm.backend.domain.incident.Incident;
import com.iitsm.backend.domain.incident.IncidentStatus;
import com.iitsm.backend.domain.user.User;
import com.iitsm.backend.repository.AttachmentRepository;
import com.iitsm.backend.repository.AttachmentRepository.AttachmentRow;
import com.iitsm.backend.repository.IncidentRepository;
import com.iitsm.backend.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;

/**
 * Photos and screenshots on incidents. Anyone who can see an incident can see and add its attachments (employees
 * only on incidents they reported), until the incident is closed or cancelled.
 */
@Service
@Transactional
public class AttachmentService {

    private final AttachmentRepository attachments;
    private final IncidentRepository incidents;
    private final IncidentPermissions permissions;
    private final CurrentUserService currentUser;
    private final Clock clock;

    public AttachmentService(AttachmentRepository attachments, IncidentRepository incidents,
                             IncidentPermissions permissions, CurrentUserService currentUser, Clock clock) {
        this.attachments = attachments;
        this.incidents = incidents;
        this.permissions = permissions;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AttachmentView> list(Long incidentId) {
        visibleIncident(incidentId);
        return attachments.listForIncident(incidentId).stream().map(AttachmentService::toView).toList();
    }

    public AttachmentView add(Long incidentId, String fileName, String declaredType, byte[] content) {
        User user = currentUser.require();
        Incident incident = visibleIncident(incidentId, user);
        if (incident.getStatus() == IncidentStatus.CLOSED || incident.getStatus() == IncidentStatus.CANCELLED) {
            throw new BusinessRuleException("ATTACHMENTS_LOCKED", "Photos cannot be added to a closed or cancelled incident");
        }
        if (content == null || content.length == 0) {
            throw new InvalidInputException("The file is empty");
        }
        if (content.length > Attachment.MAX_SIZE_BYTES) {
            throw new InvalidInputException("A photo can be at most 5 MB");
        }
        // Trust the bytes, not the client's label: the stored type comes from the file's signature.
        String type = detectImageType(content);
        if (type == null || !Attachment.ALLOWED_TYPES.contains(type)) {
            throw new InvalidInputException("Only JPEG, PNG, WebP or HEIC photos can be attached");
        }
        if (attachments.countByIncidentId(incidentId) >= Attachment.MAX_PER_INCIDENT) {
            throw new BusinessRuleException("TOO_MANY_ATTACHMENTS", "An incident can have at most " + Attachment.MAX_PER_INCIDENT + " photos");
        }
        Attachment saved = attachments.save(new Attachment(incident, user, cleanName(fileName, type), type, content, clock.instant()));
        return new AttachmentView(saved.getId(), saved.getFileName(), saved.getContentType(), saved.getSizeBytes(),
                person(user), saved.getCreatedAt());
    }

    /** The attachment with its bytes, after checking the user may see its incident. */
    @Transactional(readOnly = true)
    public Attachment content(Long attachmentId) {
        Attachment attachment = attachments.findById(attachmentId).orElseThrow(() -> new NotFoundException("Attachment", attachmentId));
        permissions.requireVisible(currentUser.require(), attachment.getIncident());
        return attachment;
    }

    private Incident visibleIncident(Long incidentId) {
        return visibleIncident(incidentId, currentUser.require());
    }

    private Incident visibleIncident(Long incidentId, User user) {
        Incident incident = incidents.findById(incidentId).orElseThrow(() -> new NotFoundException("Incident", incidentId));
        permissions.requireVisible(user, incident);
        return incident;
    }

    /** Image type from the file's magic bytes; null when it is not a supported image. */
    static String detectImageType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && Arrays.equals(Arrays.copyOf(b, 8), new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'})) {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        if (b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
            String brand = new String(b, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (brand.startsWith("hei") || brand.startsWith("hev") || brand.equals("mif1") || brand.equals("msf1")) {
                return "image/heic";
            }
        }
        return null;
    }

    /** A safe display name: no path, limited characters, extension matching the real type. */
    private static String cleanName(String original, String type) {
        String base = original == null ? "" : original.replaceAll("^.*[\\\\/]", "");
        base = base.replaceAll("\\.[A-Za-z0-9]{1,5}$", "").replaceAll("[^A-Za-z0-9 ._-]", "_").trim();
        if (base.isEmpty()) {
            base = "photo";
        }
        if (base.length() > 180) {
            base = base.substring(0, 180);
        }
        return base + "." + type.substring("image/".length()).replace("jpeg", "jpg");
    }

    private static AttachmentView toView(AttachmentRow row) {
        return new AttachmentView(row.id(), row.fileName(), row.contentType(), row.sizeBytes(), person(row.uploadedBy()), row.createdAt());
    }

    private static PersonRef person(User user) {
        return new PersonRef(user.getId(), user.getFullName(), user.getRole());
    }
}
