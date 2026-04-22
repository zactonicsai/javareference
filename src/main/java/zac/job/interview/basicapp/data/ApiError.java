package zac.job.interview.basicapp.data;


import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ApiError {

    private Instant timestamp;
    private int status;
    private String error;
    private String message;
    private String path;

    // Optional extras
    private List<String> details;
}