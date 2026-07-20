package kg.equeue.backend.qr;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import kg.equeue.backend.qr.QrDtos.QrConfigResponse;
import kg.equeue.backend.qr.QrDtos.QrCreateTicketRequest;
import kg.equeue.backend.tickets.TicketDtos.TicketResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/qr")
@Tag(name = "QR Self Service")
public class QrSelfServiceController {

    private final QrSelfServiceService qrSelfServiceService;

    public QrSelfServiceController(QrSelfServiceService qrSelfServiceService) {
        this.qrSelfServiceService = qrSelfServiceService;
    }

    @GetMapping("/departments/{departmentId}/config")
    QrConfigResponse config(@PathVariable UUID departmentId) {
        return qrSelfServiceService.config(departmentId);
    }

    @PostMapping("/tickets")
    TicketResponse createTicket(@Valid @RequestBody QrCreateTicketRequest request, HttpServletRequest httpRequest) {
        return qrSelfServiceService.createTicket(request, httpRequest);
    }
}
