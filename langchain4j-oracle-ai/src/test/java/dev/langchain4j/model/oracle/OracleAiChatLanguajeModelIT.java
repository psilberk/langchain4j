package dev.langchain4j.model.oracle;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.oracleai.OracleAiChatLanguageModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OracleAiChatLanguajeModelIT {

    ChatLanguageModel model = OracleAiChatLanguageModel.builder()
            .compartmentId("ocid1.compartment.oc1..aaaaaaaa4mpopfx7lduzttfzhxetxvbgrx4mghvl7tsp77f2epmkea3puavq")
            .modelId("ocid1.generativeaimodel.oc1.eu-frankfurt-1.amaaaaaask7dceyazi3cpmptwa52f7dgwyskloughcxtjgrqre3pngwtig4q")
            .endpoint("https://inference.generativeai.eu-frankfurt-1.oci.oraclecloud.com")
            .build();

    @Test
    void should_send_user_message_and_return_string_response() {

        // given
        String userMessage =
                "Talk to me about Messi, in a funny and short way";

        // when
        String response = model.generate(userMessage);

        // then
        assertThat(response).isNotBlank();

        // TODO Remove this
        System.out.println(response);

    }
}