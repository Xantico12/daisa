package daisa.orchestration;

import daisa.TestSupport;
import daisa.ai.AiEngineType;
import daisa.ai.MockCloudAiClient;
import daisa.ai.MockLocalAiClient;
import daisa.study.StudyTask;
import daisa.study.StudyTaskType;

import java.nio.file.Paths;

public final class AiRouterTest {
    private AiRouterTest() {
    }

    public static void run() {
        AiRouter router = new AiRouter(new MockLocalAiClient(), new MockCloudAiClient());

        StudyTask privateTask = new StudyTask(
                StudyTaskType.SUMMARIZE_NOTE,
                Paths.get("private.md"),
                "short prompt",
                true
        );
        TestSupport.assertEquals(AiEngineType.LOCAL, router.decide(privateTask).engineType());

        StudyTask explainTask = new StudyTask(
                StudyTaskType.EXPLAIN_TOPIC,
                Paths.get("distributed-systems.md"),
                "Explain consensus protocols",
                false
        );
        TestSupport.assertEquals(AiEngineType.CLOUD, router.decide(explainTask).engineType());

        StudyTask todoTask = new StudyTask(
                StudyTaskType.EXTRACT_TODOS,
                Paths.get("lecture.md"),
                "Extract todos",
                false
        );
        TestSupport.assertEquals(AiEngineType.LOCAL, router.decide(todoTask).engineType());
    }
}

